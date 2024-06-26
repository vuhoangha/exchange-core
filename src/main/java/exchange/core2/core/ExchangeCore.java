/*
 * Copyright 2019 Maksim Zheravin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package exchange.core2.core;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.lmax.disruptor.dsl.ProducerType;
import exchange.core2.core.common.CoreWaitStrategy;
import exchange.core2.core.common.cmd.CommandResultCode;
import exchange.core2.core.common.cmd.OrderCommand;
import exchange.core2.core.common.cmd.OrderCommandType;
import exchange.core2.core.common.config.ExchangeConfiguration;
import exchange.core2.core.common.config.PerformanceConfiguration;
import exchange.core2.core.common.config.SerializationConfiguration;
import exchange.core2.core.orderbook.IOrderBook;
import exchange.core2.core.processors.*;
import exchange.core2.core.processors.journaling.ISerializationProcessor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.ObjLongConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Main exchange core class.
 * Builds configuration and starts disruptor.
 */
@Slf4j
public final class ExchangeCore {

    private final Disruptor<OrderCommand> disruptor;

    private final RingBuffer<OrderCommand> ringBuffer;

    private final ExchangeApi api;

    private final ISerializationProcessor serializationProcessor;

    private final ExchangeConfiguration exchangeConfiguration;

    // core can be started and stopped only once
    private boolean started = false;
    private boolean stopped = false;

    // enable MatcherTradeEvent pooling
    public static final boolean EVENTS_POOLING = false;

    /**
     * Exchange core constructor.
     *
     * @param resultsConsumer       - custom consumer of processed commands
     * @param exchangeConfiguration - exchange configuration
     */
    @Builder
    public ExchangeCore(final ObjLongConsumer<OrderCommand> resultsConsumer,
                        final ExchangeConfiguration exchangeConfiguration) {

        log.debug("Building exchange core from configuration: {}", exchangeConfiguration);

        this.exchangeConfiguration = exchangeConfiguration;

        final PerformanceConfiguration perfCfg = exchangeConfiguration.getPerformanceCfg();

        final int ringBufferSize = perfCfg.getRingBufferSize();

        final ThreadFactory threadFactory = perfCfg.getThreadFactory();

        final CoreWaitStrategy coreWaitStrategy = perfCfg.getWaitStrategy();

        // khởi tạo disruptor cho phép nhiều producer ghi cùng lúc
        this.disruptor = new Disruptor<>(
                OrderCommand::new,  // tạo ra sẵn các Obj OrderCommand với số lượng bằng "ringBufferSize", nhằm tránh phải tạo Obj mới khi publish data và giảm tải cho GC
                ringBufferSize,     // kích thước của ring_buffer, phải đảm bảo là lũy thừa của 2 để có thể dùng phép chia lấy dư bitwise tăng hiệu suất
                threadFactory,      // tạo ra các thread để consumer xử lý msg. Có bao nhiêu consumer thì từng đó thread
                ProducerType.MULTI, // config cho phép nhiều producer có thể gửi msg cùng lúc. Tuy nhiên hiệu năng sẽ kém hơn Single do phải sử dụng cơ chế lock
                coreWaitStrategy.getDisruptorWaitStrategyFactory().get());  // chiến lược chờ đợi event mới của consumer

        this.ringBuffer = disruptor.getRingBuffer();

        this.api = new ExchangeApi(ringBuffer, perfCfg.getBinaryCommandsLz4CompressorFactory().get());

        // OrderBookNaiveImpl || OrderBookDirectImpl
        final IOrderBook.OrderBookFactory orderBookFactory = perfCfg.getOrderBookFactory();

        // lay so luong matching engine va risk engine can chay
        final int matchingEnginesNum = perfCfg.getMatchingEnginesNum();
        final int riskEnginesNum = perfCfg.getRiskEnginesNum();

        // creating serialization processor
        final SerializationConfiguration serializationCfg = exchangeConfiguration.getSerializationCfg();
        serializationProcessor = serializationCfg.getSerializationProcessorFactory().apply(exchangeConfiguration);

        // creating shared objects pool
        final int poolInitialSize = (matchingEnginesNum + riskEnginesNum) * 8;
        final int chainLength = EVENTS_POOLING ? 1024 : 1;
        final SharedPool sharedPool = new SharedPool(poolInitialSize * 4, poolInitialSize, chainLength);

        // handler exception cua disruptor
        final DisruptorExceptionHandler<OrderCommand> exceptionHandler = new DisruptorExceptionHandler<>("main", (ex, seq) -> {
            log.error("Exception thrown on sequence={}", seq, ex);
            ringBuffer.publishEvent(SHUTDOWN_SIGNAL_TRANSLATOR);
            disruptor.shutdown();
        });
        disruptor.setDefaultExceptionHandler(exceptionHandler);

        // advice completable future to use the same CPU socket as disruptor
        // excutor_service dùng để tạo thread để chạy các matching_engine và risk_engine
        // thread factory nhằm tạo ra thread mới nếu cần cho ExecutorService
        final ExecutorService loaderExecutor = Executors.newFixedThreadPool(matchingEnginesNum + riskEnginesNum, threadFactory);

        // start creating matching engines
        // tạo các matching_engine với số lượng "matchingEnginesNum", shardId bắt đầu từ 0 --> matchingEnginesNum - 1
        final Map<Integer, CompletableFuture<MatchingEngineRouter>> matchingEngineFutures = IntStream.range(0, matchingEnginesNum)
                .boxed()
                .collect(Collectors.toMap(
                        shardId -> shardId,
                        shardId -> CompletableFuture.supplyAsync(
                                () -> new MatchingEngineRouter(shardId, matchingEnginesNum, serializationProcessor, orderBookFactory, sharedPool, exchangeConfiguration),
                                loaderExecutor)));

        // TODO create processors in same thread we will execute it??

        // start creating risk engines
        // tạo các risk_engine với số lượng "riskEnginesNum", shardId bắt đầu từ 0 --> riskEnginesNum - 1
        final Map<Integer, CompletableFuture<RiskEngine>> riskEngineFutures = IntStream.range(0, riskEnginesNum)
                .boxed()
                .collect(Collectors.toMap(
                        shardId -> shardId,
                        shardId -> CompletableFuture.supplyAsync(
                                () -> new RiskEngine(shardId, riskEnginesNum, serializationProcessor, sharedPool, exchangeConfiguration),
                                loaderExecutor)));

        // tạo các matching_engine_handler từ function "MatchingEngineRouter.processOrder"
        // nghia la disruptor sau khi nhan duoc 1 msg trong ring buffer se dung thang MatchingEngineRouter.processOrder xu ly
        final EventHandler<OrderCommand>[] matchingEngineHandlers = matchingEngineFutures.values().stream()
                .map(CompletableFuture::join)   // thực hiện các task tạo matching_engine phía trên
                .map(mer -> (EventHandler<OrderCommand>) (cmd, seq, eob) -> mer.processOrder(seq, cmd)) // tạo các handler từ matching_engine phía trên
                .toArray(ExchangeCore::newEventHandlersArray);  // convert từ stream sang array

        // lấy tất cả các risk engine đã được khởi tạo, key là shardId
        final Map<Integer, RiskEngine> riskEngines = riskEngineFutures.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().join()));

        final List<TwoStepMasterProcessor> procR1 = new ArrayList<>(riskEnginesNum);
        final List<TwoStepSlaveProcessor> procR2 = new ArrayList<>(riskEnginesNum);

        // 1. grouping processor (G)
        // GroupingProcessor sẽ xử lý data đầu vào từ ring_buffer trước
        final EventHandlerGroup<OrderCommand> afterGrouping =
                disruptor.handleEventsWith(
                        (_ringBuffer, _sequences) -> new GroupingProcessor(_ringBuffer, _ringBuffer.newBarrier(_sequences), perfCfg, coreWaitStrategy, sharedPool));

        // 2. [journaling (J)] in parallel with risk hold (R1) + matching engine (ME)
        // chạy sau GroupingProcessor dùng để ghi log
        boolean enableJournaling = serializationCfg.isEnableJournaling();
        final EventHandler<OrderCommand> journalingHandler = enableJournaling ? serializationProcessor::writeToJournal : null;
        if (enableJournaling) {
            // hàm này sẽ add 'journalingHandler' thực hiện sau khi 'GroupingProcessor' hoàn thành
            afterGrouping.handleEventsWith(journalingHandler);
        }
        afterGrouping.handleEventsWith(this::writelog);

        // chạy sau khi GroupingProcessor hoàn thành và song song với việc ghi log
        // nó sử dụng thằng "riskEngine::preProcessCommand" để check xem user có đủ số dư ko, hoặc nếu có margin thì có đang bị quá hạn mức ko
        // đoạn này có nghĩa là nó chờ thằng "TwoStepMasterProcessor.processEvents", trong thằng ấy nó gọi đến "riskEngine::preProcessCommand"
        // và sau khi xử lý xong nó publish vào ring_buffer --> call tới step tiếp theo
        riskEngines.forEach((idx, riskEngine) -> afterGrouping.handleEventsWith(
                (_ringBuffer, _sequence) -> {
                    final TwoStepMasterProcessor r1 = new TwoStepMasterProcessor(_ringBuffer, _ringBuffer.newBarrier(_sequence), riskEngine::preProcessCommand, exceptionHandler, coreWaitStrategy, "R1_" + idx);
                    procR1.add(r1);
                    return r1;
                }));

        // procR1.toArray(new TwoStepMasterProcessor[0])    --> convert "List<TwoStepMasterProcessor> procR1" sang một array[TwoStepMasterProcessor]
        // chạy sau thằng 'riskEngine::preProcessCommand' sau khi đã check số dư + risk position ngon lành
        // đoạn này nó sẽ xử lý khớp lệnh thông qua hàm "MatchingEngineRouter.processOrder(seq, cmd)"
        disruptor.after(procR1.toArray(new TwoStepMasterProcessor[0])).handleEventsWith(matchingEngineHandlers);

        // 3. risk release (R2) after matching engine (ME)
        final EventHandlerGroup<OrderCommand> afterMatchingEngine = disruptor.after(matchingEngineHandlers);

        riskEngines.forEach((idx, riskEngine) -> afterMatchingEngine.handleEventsWith(
                (_ringBuffer, _sequences) -> {
                    final TwoStepSlaveProcessor r2 = new TwoStepSlaveProcessor(_ringBuffer, _ringBuffer.newBarrier(_sequences), riskEngine::handlerRiskRelease, exceptionHandler, "R2_" + idx);
                    procR2.add(r2);
                    return r2;
                }));


        // 4. results handler (E) after matching engine (ME) + [journaling (J)]
        final EventHandlerGroup<OrderCommand> mainHandlerGroup = enableJournaling
                ? disruptor.after(arraysAddHandler(matchingEngineHandlers, journalingHandler))
                : afterMatchingEngine;

        final ResultsHandler resultsHandler = new ResultsHandler(resultsConsumer);

        mainHandlerGroup.handleEventsWith((cmd, seq, eob) -> {
            resultsHandler.onEvent(cmd, seq, eob);
            api.processResult(seq, cmd); // TODO SLOW ?(volatile operations)
        });

        // attach slave processors to master processor
        IntStream.range(0, riskEnginesNum).forEach(i -> procR1.get(i).setSlaveProcessor(procR2.get(i)));

        try {
            loaderExecutor.shutdown();
            loaderExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writelog(OrderCommand cmd, long dSeq, boolean eob){
        try{
//            Thread.sleep(500);
            log.info(MessageFormat.format("Writelog - cmd: {0}, dSeq: {1}, eob: {2}",  cmd.toString(), dSeq, eob));
        }catch (Exception ex){}
    }

    public synchronized void startup() {
        if (!started) {
            log.debug("Starting disruptor...");
            disruptor.start();
            started = true;

            serializationProcessor.replayJournalFullAndThenEnableJouraling(exchangeConfiguration.getInitStateCfg(), api);
        }
    }

    /**
     * Provides ExchangeApi instance.
     *
     * @return ExchangeApi instance (always same object)
     */
    public ExchangeApi getApi() {
        return api;
    }

    private static final EventTranslator<OrderCommand> SHUTDOWN_SIGNAL_TRANSLATOR = (cmd, seq) -> {
        cmd.command = OrderCommandType.SHUTDOWN_SIGNAL;
        cmd.resultCode = CommandResultCode.NEW;
    };

    /**
     * shut down disruptor
     */
    public synchronized void shutdown() {
        shutdown(-1, TimeUnit.MILLISECONDS);
    }

    /**
     * Will throw IllegalStateException if an exchange core can not stop gracefully.
     *
     * @param timeout  the amount of time to wait for all events to be processed. <code>-1</code> will give an infinite timeout
     * @param timeUnit the unit the timeOut is specified in
     */
    public synchronized void shutdown(final long timeout, final TimeUnit timeUnit) {
        if (!stopped) {
            stopped = true;
            // TODO stop accepting new events first
            try {
                log.info("Shutdown disruptor...");
                ringBuffer.publishEvent(SHUTDOWN_SIGNAL_TRANSLATOR);
                disruptor.shutdown(timeout, timeUnit);
                log.info("Disruptor stopped");
            } catch (TimeoutException e) {
                throw new IllegalStateException("could not stop a disruptor gracefully. Not all events may be executed.");
            }
        }
    }

    private static EventHandler<OrderCommand>[] arraysAddHandler(EventHandler<OrderCommand>[] handlers, EventHandler<OrderCommand> extraHandler) {
        final EventHandler<OrderCommand>[] result = Arrays.copyOf(handlers, handlers.length + 1);
        result[handlers.length] = extraHandler;
        return result;
    }

    @SuppressWarnings(value = {"unchecked"})
    private static EventHandler<OrderCommand>[] newEventHandlersArray(int size) {
        return new EventHandler[size];
    }
}
