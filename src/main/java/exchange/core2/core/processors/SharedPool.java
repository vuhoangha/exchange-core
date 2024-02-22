/*
 * Copyright 2020 Maksim Zheravin
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

import exchange.core2.core.common.MatcherTradeEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public final class SharedPool {

    // queue theo dạng FIFO
    // ko giới hạn kích thước
    // thread-safe
    private final LinkedBlockingQueue<MatcherTradeEvent> eventChainsBuffer;

    @Getter
    private final int chainLength;

    public static SharedPool createTestSharedPool() {
        return new SharedPool(8, 4, 256);
    }

    /**
     * Create new shared pool
     *
     * @param poolMaxSize     - kích thước tối đa của pool, nếu pool full thì sẽ ko thể insert thêm vào nữa. (max size of pool. Will skip new chains if chains buffer is full).
     * @param poolInitialSize - số lượng khởi tạo của các chains generated trước. (initial number of pre-generated chains. Recommended to set higher than number of modules - (RE+ME)*2).
     * @param chainLength     - độ dài chuỗi khởi tạo (target chain length. Longer chain means rare requests for new chains. However longer chains can cause event placeholders starvation).
     */
    public SharedPool(final int poolMaxSize, final int poolInitialSize, final int chainLength) {

        // size init mà lớn hơn size max của pool thì error
        if (poolInitialSize > poolMaxSize) {
            throw new IllegalArgumentException("too big poolInitialSize");
        }

        // tạo pool với max size như trên
        this.eventChainsBuffer = new LinkedBlockingQueue<>(poolMaxSize);
        this.chainLength = chainLength;

        // tạo các chain với độ dài cố định
        for (int i = 0; i < poolInitialSize; i++) {
            this.eventChainsBuffer.add(MatcherTradeEvent.createEventChain(chainLength));
        }
    }

    /**
     * Lấy phần tử head của Pool này. Nếu ko có thì tự khởi tạo rồi trả về
     *
     * Request next chain from buffer
     * Threadsafe
     *
     * @return chain, otherwise null
     */
    public MatcherTradeEvent getChain() {
        MatcherTradeEvent poll = eventChainsBuffer.poll();
        if (poll == null) {
            poll = MatcherTradeEvent.createEventChain(chainLength);
        }

        return poll;
    }

    /**
     *
     *
     * Offers next chain.
     * Threadsafe (single producer safety is sufficient)
     *
     * @param head - pointer to the first element
     */
    public void putChain(MatcherTradeEvent head) {
        boolean offer = eventChainsBuffer.offer(head);
//        log.debug(">>> OFFER CHAIN HEAD  size={} orrder={}", head.getChainSize(), offer);
    }

}
