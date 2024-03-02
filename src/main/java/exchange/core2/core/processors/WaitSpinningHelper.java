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
 * Hỗ trợ consumer chờ đợi seq theo từng strategy mà nó đặt ra
 */
@Slf4j
public final class WaitSpinningHelper {

    private final SequenceBarrier sequenceBarrier;  // theo dõi các seq đã sẵn sàng để đọc trong ring_buffer, được các producer đẩy vào
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
        this.sequenceBarrier = sequenceBarrier;       // theo dõi các seq đã sẵn sàng để đọc trong ring_buffer, được các producer đẩy vào
        this.spinLimit = spinLimit;     // giới hạn số lần quay để kiểm tra xem đã tới seq đang chờ chưa
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


    /**
     * consumer chờ đợi 'seq' được producer bắn vào trong ring_buffer
     * nếu trong số lần spin/yield cho phép có available_seq >= seq thì
     *      nếu là Single Producer: trả ra available_seq
     *      nếu là Multi Producer: trả ra sequence được publish gần nhất trong khoảng (seq, available_seq)
     * nếu quá số lần spin/yield chờ thì sẽ có 2 trường hợp
     *      trả về "seq - 1" là seq gần nhất được publish vào ring_buffer và đã được consumer này xử lý
     *      trả về available_seq là seq gần nhất được ghi nhận từ ring_buffer trong hàm này
     *
     * @param seq: seq tiếp theo chưa được xử lý trong ring_buffer
     */
    public long tryWaitFor(final long seq) throws AlertException, InterruptedException {
        // kiểm tra xem có "alert" chưa. Nếu có thì throw ra AlertException
        // alert ở đây nghĩa là process rơi vào trạng thái tạm dừng(HALT) từ trước đó nên nó sẽ ngừng xử lý các yêu cầu tiếp theo
        sequenceBarrier.checkAlert();

        long spin = spinLimit;
        long availableSequence;

        // sequenceBarrier.getCursor(): lấy vị trí mới nhất được producer ghi vào
        // kiểm tra xem sequence hiện tại có phải sequence mong muốn ko, nếu ko phải thì chờ đợi
        while ((availableSequence = sequenceBarrier.getCursor()) < seq && spin > 0) {
            if (spin < yieldLimit && spin > 1) {
                // nếu là strategy là yield
                // #desc đoạn này hơi lấn cấn. Mục đích của nó là nhường cho thread khác hoạt động. Thế nếu sau khi đạt tới giới hạn yieldLimit mà seq vẫn ko có thì nó làm ntn
                Thread.yield();
            } else if (block) {
                // chiếm lock, nếu có được lock thì mới đi tới bước tiếp
                lock.lock();
                try {
                    // check xem disruptor còn running ko
                    sequenceBarrier.checkAlert();
                    // nếu từ lúc gần nhất lấy sequenceBarrier.getCursor() mà nó vẫn ko thay đổi gì --> await tiếp
                    if (availableSequence == sequenceBarrier.getCursor()) {
                        // chờ đợi 1 thread khác hoàn thành và notify
                        processorNotifyCondition.await();
                    }
                } finally {
                    // unlock sau khi xử lý xong
                    lock.unlock();
                }
            }

            // trừ đi 1 lượt quay
            spin--;
        }

        return (availableSequence < seq)
                ? availableSequence // nếu chờ đợi đủ spin/yield limit rồi mà vẫn chưa tới seq mong muốn --> trả về seq gần nhất ghi nhận
                : sequencer.getHighestPublishedSequence(seq, availableSequence);    // lấy seq được producer publish gần nhất nằm trong khoảng (seq, availableSequence)
                // nếu là Single Producer thì nó sẽ trả về ngay "availableSequence" vì SequenceBarrier đã trả ra giá trị nào thì đồng nghĩa nó đã publish seq đó
                // còn nếu Multi Producer thì nó sẽ phải check từng seq trong khoảng đó. Vì các producer publish song song nên sẽ có trường hợp seq cao hơn được publish bởi producer A, seq thấp hơn đang chờ producer B publish
                // nếu 'seq' chưa được publish thì nó sẽ trả về 'seq - 1'
    }


    public void signalAllWhenBlocking() {
        if (block) {
            blockingDisruptorWaitStrategy.signalAllWhenBlocking();
        }
    }


    // lấy trực tiếp đối tượng 'Sequencer' từ Ring_buffer. Vì mặc định nó ko được public nên phải dùng Reflection
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
