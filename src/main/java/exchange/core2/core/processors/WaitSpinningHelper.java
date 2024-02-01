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

import com.lmax.disruptor.*;
import exchange.core2.core.common.CoreWaitStrategy;
import exchange.core2.core.utils.ReflectionUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Class này dùng để lên chiến lược chờ đợi cho các processor.
 * Để chờ tới 1 seq cụ thể thì CPU cần làm cụ thể ntn
 */
@Slf4j
public final class WaitSpinningHelper {

    private final SequenceBarrier sequenceBarrier;
    private final Sequencer sequencer;

    private final int spinLimit;    // Giới hạn cho việc spin-waiting (đợi bằng cách quay vòng)
    private final int yieldLimit;   // Giới hạn yield-waiting (đợi bằng cách nhường thời gian cho các luồng khác)

    // blocking mode, using same locking objects that Disruptor operates with
    private final boolean block;    // check xem có phải đang ở blocking mode ko
    private final BlockingWaitStrategy blockingDisruptorWaitStrategy;
    private final Lock lock;    // chỉ sử dụng nếu strategy là 'Block'
    private final Condition processorNotifyCondition;   // điều kiện (condition) sử dụng trong chế độ block để chờ đợi và notify.
    // next Disruptor release will have mutex (to avoid allocations)
    // private final Object mutex;

    public <T> WaitSpinningHelper(RingBuffer<T> ringBuffer, SequenceBarrier sequenceBarrier, int spinLimit, CoreWaitStrategy waitStrategy) {
        this.sequenceBarrier = sequenceBarrier;
        this.spinLimit = spinLimit;
        this.sequencer = extractSequencer(ringBuffer);
        this.yieldLimit = waitStrategy.isYield() ? spinLimit / 2 : 0;   // nếu wait_strategy là yield = spinLimit / 2, vì vốn dĩ yield là nhường CPU cho thread khác nên tốc độ xử lý cũng nhanh hơn vì ko cần dùng đến lock. Các thread khaác xử lý nhanh dẫn tới số lần quay để chờ đợi ngắn hơn nên spin/2

        this.block = waitStrategy.isBlock();
        if (block) {
            this.blockingDisruptorWaitStrategy = ReflectionUtils.extractField(AbstractSequencer.class, (AbstractSequencer) sequencer, "waitStrategy");
            this.lock = ReflectionUtils.extractField(BlockingWaitStrategy.class, blockingDisruptorWaitStrategy, "lock");
            this.processorNotifyCondition = ReflectionUtils.extractField(BlockingWaitStrategy.class, blockingDisruptorWaitStrategy, "processorNotifyCondition");
        } else {
            this.blockingDisruptorWaitStrategy = null;
            this.lock = null;
            this.processorNotifyCondition = null;
        }
    }

    // #desc có vẻ là chờ tới cái sequence mong muốn
    public long tryWaitFor(final long seq) throws AlertException, InterruptedException {
        // kiểm tra xem có "alert" chưa. Nếu có thì throw ra AlertException
        // alert ở đây nghĩa là process rơi vào trạng thái tạm dừng(HALT) từ trước đó nên nó sẽ ngừng xử lý các yêu cầu tiếp theo
        sequenceBarrier.checkAlert();

        long spin = spinLimit;
        long availableSequence;

        // kiểm tra xem sequence hiện tại có phải sequence mong muốn ko, nếu ko phải thì chờ đợi
        while ((availableSequence = sequenceBarrier.getCursor()) < seq && spin > 0) {
            if (spin < yieldLimit && spin > 1) {
                Thread.yield();     // #desc đoạn này hơi lấn cấn. Mục đích của nó là nhường cho thread khác hoạt động. Thế nếu sau khi đạt tới giới hạn yieldLimit mà seq vẫn ko có thì nó làm ntn
            } else if (block) {
                // chiếm lock, nếu có được lock thì mới đi tới bước tiếp
                lock.lock();
                try {
                    sequenceBarrier.checkAlert();
                    // chỉ await nếu sequence barrier ko có update nào kể từ lần check gần nhất
                    if (availableSequence == sequenceBarrier.getCursor()) {
                        // chờ đợi 1 thread khác hoàn thành và notify
                        processorNotifyCondition.await();
                    }
                } finally {
                    lock.unlock();
                }
            }

            spin--;
        }

        return (availableSequence < seq)
                ? availableSequence // nếu chờ đợi đủ spin/yield limit rồi mà vẫn chưa tới seq mong muốn --> trả về seq gần nhất ghi nhận
                : sequencer.getHighestPublishedSequence(seq, availableSequence);
    }

    public void signalAllWhenBlocking() {
        if (block) {
            blockingDisruptorWaitStrategy.signalAllWhenBlocking();
        }
    }

    private static <T> Sequencer extractSequencer(RingBuffer<T> ringBuffer) {
        try {
            final Field f = ReflectionUtils.getField(RingBuffer.class, "sequencer");
            f.setAccessible(true);
            return (Sequencer) f.get(ringBuffer);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Can not access Disruptor internals: ", e);
        }
    }
}
