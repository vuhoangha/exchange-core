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
package exchange.core2.core.processors;

import exchange.core2.collections.objpool.ObjectsPool;
import exchange.core2.core.common.CoreSymbolSpecification;
import exchange.core2.core.common.SymbolType;
import exchange.core2.core.common.api.binary.BatchAddAccountsCommand;
import exchange.core2.core.common.api.binary.BatchAddSymbolsCommand;
import exchange.core2.core.common.api.reports.ReportQuery;
import exchange.core2.core.common.api.reports.ReportResult;
import exchange.core2.core.common.cmd.CommandResultCode;
import exchange.core2.core.common.cmd.OrderCommand;
import exchange.core2.core.common.cmd.OrderCommandType;
import exchange.core2.core.common.config.*;
import exchange.core2.core.orderbook.IOrderBook;
import exchange.core2.core.orderbook.OrderBookEventsHelper;
import exchange.core2.core.processors.journaling.DiskSerializationProcessorConfiguration;
import exchange.core2.core.processors.journaling.ISerializationProcessor;
import exchange.core2.core.utils.SerializationUtils;
import exchange.core2.core.utils.UnsafeUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.WriteBytesMarshallable;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Optional;

@Slf4j
@Getter
public final class MatchingEngineRouter implements WriteBytesMarshallable {

    public static final ISerializationProcessor.SerializedModuleType MODULE_ME =
            ISerializationProcessor.SerializedModuleType.MATCHING_ENGINE_ROUTER;

    // state
    private final BinaryCommandsProcessor binaryCommandsProcessor;

    // symbol -> order_book
    private final IntObjectHashMap<IOrderBook> orderBooks;

    private final IOrderBook.OrderBookFactory orderBookFactory;

    private final OrderBookEventsHelper eventsHelper;

    // local objects pool for order books
    private final ObjectsPool objectsPool;

    // sharding by symbolId
    // hệ thống có nhiều symbol và các symbol sẽ được gom vào từng shard, mỗi shard sẽ được xử lý bởi 1 matching engine
    private final int shardId;
    // là 1 mặt nạ có dạng 1111..(n số '1') có tác dụng để tìm shard_id từ id của symbol.
    // Ví dụ ta chia các symbol làm 8 shard, thì shardMask = 7 (111). Muốn tìm shardId ta lấy (symbol_id & shardMask)
    // bản chất là phép modulo thôi nhưng performance tốt hơn
    private final long shardMask;

    private final String exchangeId; // TODO validate
    private final Path folder;

    // có cho phép chế độ margin ko
    private final boolean cfgMarginTradingEnabled;

    // có gửi order_book của symbol sau mỗi order thành công ko. Vì order_book cần thiết cho risk_engine
    private final boolean cfgSendL2ForEveryCmd;
    // độ sâu của market deep gửi kèm sau mỗi giao dịch thành công
    private final int cfgL2RefreshDepth;

    private final ISerializationProcessor serializationProcessor;

    private final LoggingConfiguration loggingCfg;
    private final boolean logDebug;

    public MatchingEngineRouter(final int shardId,      // shard_id của matching engine instance hiện tại
                                final long numShards,   // tổng số lượng shard cho các symbol
                                final ISerializationProcessor serializationProcessor,
                                final IOrderBook.OrderBookFactory orderBookFactory,     // factory tạo orderbook khi cần. Naive | Direct tùy thuộc config
                                final SharedPool sharedPool,
                                final ExchangeConfiguration exchangeCfg) {  // config hệ thống để chạy
        // đếm số bit có giá trị 1. Các số là bội số của 2 sẽ luôn có dạng 1000xxx (với n số '0' phía sau)
        if (Long.bitCount(numShards) != 1) {
            throw new IllegalArgumentException("Invalid number of shards " + numShards + " - must be power of 2");
        }

        final InitialStateConfiguration initStateCfg = exchangeCfg.getInitStateCfg();

        this.exchangeId = initStateCfg.getExchangeId();
        this.folder = Paths.get(DiskSerializationProcessorConfiguration.DEFAULT_FOLDER);

        // shard_id và shard_mask
        this.shardId = shardId;
        this.shardMask = numShards - 1;
        this.serializationProcessor = serializationProcessor;
        this.orderBookFactory = orderBookFactory;
        this.eventsHelper = new OrderBookEventsHelper(sharedPool::getChain);

        this.loggingCfg = exchangeCfg.getLoggingCfg();
        this.logDebug = loggingCfg.getLoggingLevels().contains(LoggingConfiguration.LoggingLevel.LOGGING_MATCHING_DEBUG);

        // initialize object pools // TODO move to perf config
        // #desc chưa hiểu làm gì
        final HashMap<Integer, Integer> objectsPoolConfig = new HashMap<>();
        objectsPoolConfig.put(ObjectsPool.DIRECT_ORDER, 1024 * 1024);
        objectsPoolConfig.put(ObjectsPool.DIRECT_BUCKET, 1024 * 64);
        objectsPoolConfig.put(ObjectsPool.ART_NODE_4, 1024 * 32);
        objectsPoolConfig.put(ObjectsPool.ART_NODE_16, 1024 * 16);
        objectsPoolConfig.put(ObjectsPool.ART_NODE_48, 1024 * 8);
        objectsPoolConfig.put(ObjectsPool.ART_NODE_256, 1024 * 4);
        this.objectsPool = new ObjectsPool(objectsPoolConfig);

        if (ISerializationProcessor.canLoadFromSnapshot(serializationProcessor, initStateCfg, shardId, MODULE_ME)) {
            final DeserializedData deserialized = serializationProcessor.loadData(
                    initStateCfg.getSnapshotId(),
                    ISerializationProcessor.SerializedModuleType.MATCHING_ENGINE_ROUTER,
                    shardId,
                    bytesIn -> {
                        if (shardId != bytesIn.readInt()) {
                            throw new IllegalStateException("wrong shardId");
                        }
                        if (shardMask != bytesIn.readLong()) {
                            throw new IllegalStateException("wrong shardMask");
                        }

                        final BinaryCommandsProcessor bcp = new BinaryCommandsProcessor(
                                this::handleBinaryMessage,
                                this::handleReportQuery,
                                sharedPool,
                                exchangeCfg.getReportsQueriesCfg(),
                                bytesIn,
                                shardId + 1024);

                        final IntObjectHashMap<IOrderBook> ob = SerializationUtils.readIntHashMap(
                                bytesIn,
                                bytes -> IOrderBook.create(bytes, objectsPool, eventsHelper, loggingCfg));

                        return DeserializedData.builder().binaryCommandsProcessor(bcp).orderBooks(ob).build();
                    });

            this.binaryCommandsProcessor = deserialized.binaryCommandsProcessor;
            this.orderBooks = deserialized.orderBooks;

        } else {
            this.binaryCommandsProcessor = new BinaryCommandsProcessor(
                    this::handleBinaryMessage,
                    this::handleReportQuery,
                    sharedPool,
                    exchangeCfg.getReportsQueriesCfg(),
                    shardId + 1024);

            this.orderBooks = new IntObjectHashMap<>();
        }

        final OrdersProcessingConfiguration ordersProcCfg = exchangeCfg.getOrdersProcessingCfg();
        this.cfgMarginTradingEnabled = ordersProcCfg.getMarginTradingMode() == OrdersProcessingConfiguration.MarginTradingMode.MARGIN_TRADING_ENABLED;

        final PerformanceConfiguration perfCfg = exchangeCfg.getPerformanceCfg();
        this.cfgSendL2ForEveryCmd = perfCfg.isSendL2ForEveryCmd();
        this.cfgL2RefreshDepth = perfCfg.getL2RefreshDepth();
    }


    public void processOrder(long seq, OrderCommand cmd) {
        log.info(MessageFormat.format("processOrder - seq: {0}, cmd: {1}", seq, cmd.toString()));

        final OrderCommandType command = cmd.command;

        if (command == OrderCommandType.MOVE_ORDER
                || command == OrderCommandType.CANCEL_ORDER
                || command == OrderCommandType.PLACE_ORDER
                || command == OrderCommandType.REDUCE_ORDER
                || command == OrderCommandType.ORDER_BOOK_REQUEST) {
            // process specific symbol group only
            if (symbolForThisHandler(cmd.symbol)) {
                processMatchingCommand(cmd);
            }
        } else if (command == OrderCommandType.BINARY_DATA_QUERY || command == OrderCommandType.BINARY_DATA_COMMAND) {

            final CommandResultCode resultCode = binaryCommandsProcessor.acceptBinaryFrame(cmd);
            if (shardId == 0) {
                cmd.resultCode = resultCode;
            }

        } else if (command == OrderCommandType.RESET) {
            // process all symbols groups, only processor 0 writes result
            orderBooks.clear();
            binaryCommandsProcessor.reset();
            if (shardId == 0) {
                cmd.resultCode = CommandResultCode.SUCCESS;
            }

        } else if (command == OrderCommandType.NOP) {
            if (shardId == 0) {
                cmd.resultCode = CommandResultCode.SUCCESS;
            }

        } else if (command == OrderCommandType.PERSIST_STATE_MATCHING) {
            final boolean isSuccess = serializationProcessor.storeData(
                    cmd.orderId,
                    seq,
                    cmd.timestamp,
                    ISerializationProcessor.SerializedModuleType.MATCHING_ENGINE_ROUTER,
                    shardId,
                    this);
            // Send ACCEPTED because this is a first command in series. Risk engine is second - so it will return SUCCESS
            UnsafeUtils.setResultVolatile(cmd, isSuccess, CommandResultCode.ACCEPTED, CommandResultCode.STATE_PERSIST_MATCHING_ENGINE_FAILED);
        }

    }

    private void handleBinaryMessage(Object message) {

        if (message instanceof BatchAddSymbolsCommand) {
            final IntObjectHashMap<CoreSymbolSpecification> symbols = ((BatchAddSymbolsCommand) message).getSymbols();
            symbols.forEach(this::addSymbol);
        } else if (message instanceof BatchAddAccountsCommand) {
            // do nothing
        }
    }

    private <R extends ReportResult> Optional<R> handleReportQuery(ReportQuery<R> reportQuery) {
        return reportQuery.process(this);
    }


    // check xem matching_engine instance hiện tại có xử lý symbol này ko
    // check bằng cách xác định symbol thuộc shard nào, có giống shard của matching_engine instance này
    private boolean symbolForThisHandler(final long symbol) {
        return (shardMask == 0) || ((symbol & shardMask) == shardId);
    }


    private void addSymbol(final CoreSymbolSpecification spec) {
        if (spec.type != SymbolType.CURRENCY_EXCHANGE_PAIR && !cfgMarginTradingEnabled) {
            log.warn("Margin symbols are not allowed: {}", spec);
        }

        if (orderBooks.get(spec.symbolId) == null) {
            orderBooks.put(spec.symbolId, orderBookFactory.create(spec, objectsPool, eventsHelper, loggingCfg));
        } else {
            log.warn("OrderBook for symbol id={} already exists! Can not add symbol: {}", spec.symbolId, spec);
        }
    }


    private void processMatchingCommand(final OrderCommand cmd) {

        final IOrderBook orderBook = orderBooks.get(cmd.symbol);
        if (orderBook == null) {
            cmd.resultCode = CommandResultCode.MATCHING_INVALID_ORDER_BOOK_ID;
        } else {
            cmd.resultCode = IOrderBook.processCommand(orderBook, cmd);

            // quyết định xem có lấy order_book với độ sâu 'cfgL2RefreshDepth' sau mỗi order thành công ko
            //
            // posting market data for risk processor makes sense only if command execution is successful, otherwise it will be ignored (possible garbage from previous cycle)
            // TODO don't need for EXCHANGE mode order books?
            // TODO doing this for many order books simultaneously can introduce hiccups
            if ((cfgSendL2ForEveryCmd || (cmd.serviceFlags & 1) != 0)
                    && cmd.command != OrderCommandType.ORDER_BOOK_REQUEST
                    && cmd.resultCode == CommandResultCode.SUCCESS) {
                cmd.marketData = orderBook.getL2MarketDataSnapshot(cfgL2RefreshDepth);
            }
        }
    }

    @Override
    public void writeMarshallable(BytesOut bytes) {
        bytes.writeInt(shardId).writeLong(shardMask);
        binaryCommandsProcessor.writeMarshallable(bytes);

        // write orderBooks
        SerializationUtils.marshallIntHashMap(orderBooks, bytes);
    }

    @Builder
    @RequiredArgsConstructor
    private static class DeserializedData {
        private final BinaryCommandsProcessor binaryCommandsProcessor;
        private final IntObjectHashMap<IOrderBook> orderBooks;
    }
}
