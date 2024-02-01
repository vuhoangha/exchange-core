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
package exchange.core2.core.common;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO move activeOrderCompleted, eventType, section into the order?
// TODO REDUCE needs remaining size (can write into size), bidderHoldPrice - can write into price
// TODO REJECT needs remaining size (can not write into size),

/**
 * #desc
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public final class MatcherTradeEvent {

    public MatcherEventType eventType;  // TRADE, REDUCE, REJECT (rare) or BINARY_EVENT (reports data)

    // #desc ko hiểu biến section này làm cái gì nữa
    public int section;

    // TODO join (requires 11+ bits)
    // = false trừ khi activeOrder (khớp hoàn toàn / bị remove / bị reject)
    // = true nếu REJECT event
    // = true trong trường hợp REDUCE event nếu reduce được gọi bởi COMMAND
    public boolean activeOrderCompleted;

    // maker (for TRADE event type only)
    public long matchedOrderId;
    public long matchedOrderUid; // 0 for rejection
    public boolean matchedOrderCompleted; // = false, trừ khi matchedOrder khớp hoàn toàn

    // giá thực tế của giao dịch (from maker order), "0" for rejection (giá có thể được lấy từ original order)
    public long price;

    // TRADE - trade size
    // REDUCE - (khối lượng bị giảm của REDUCE command) or (khối lượng chưa khớp với CANCEL command)
    // REJECT - khối lượng chưa khớp của rejected order
    public long size;

    // giá cố định từ BID order owner (depends on activeOrderAction)
    public long bidderHoldPrice;

    // reference to next event in chain
    public MatcherTradeEvent nextEvent;


    // testing only
    public MatcherTradeEvent copy() {
        MatcherTradeEvent evt = new MatcherTradeEvent();
        evt.eventType = this.eventType;
        evt.section = this.section;
        evt.activeOrderCompleted = this.activeOrderCompleted;
        evt.matchedOrderId = this.matchedOrderId;
        evt.matchedOrderUid = this.matchedOrderUid;
        evt.matchedOrderCompleted = this.matchedOrderCompleted;
        evt.price = this.price;
        evt.size = this.size;
//        evt.timestamp = this.timestamp;
        evt.bidderHoldPrice = this.bidderHoldPrice;
        return evt;
    }

    // testing only
    public MatcherTradeEvent findTail() {
        MatcherTradeEvent tail = this;
        while (tail.nextEvent != null) {
            tail = tail.nextEvent;
        }
        return tail;
    }


    // lấy số lượng các step đã đi của Order này
    public int getChainSize() {
        MatcherTradeEvent tail = this;
        int c = 1;
        while (tail.nextEvent != null) {
            tail = tail.nextEvent;
            c++;
        }
        return c;
    }


    // khởi tạo các step của 1 Order dựa trên số lượng step mong muốn
    @NotNull
    public static MatcherTradeEvent createEventChain(int chainLength) {
        final MatcherTradeEvent head = new MatcherTradeEvent();
        MatcherTradeEvent prev = head;
        for (int j = 1; j < chainLength; j++) {
            MatcherTradeEvent nextEvent = new MatcherTradeEvent();
            prev.nextEvent = nextEvent;
            prev = nextEvent;
        }
        return head;
    }


    // testing only
    public static List<MatcherTradeEvent> asList(MatcherTradeEvent next) {
        List<MatcherTradeEvent> list = new ArrayList<>();
        while (next != null) {
            list.add(next);
            next = next.nextEvent;
        }
        return list;
    }


    /**
     * Compare next events chain as well.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof MatcherTradeEvent)) return false;
        MatcherTradeEvent other = (MatcherTradeEvent) o;

        // ignore timestamp
        return section == other.section
                && activeOrderCompleted == other.activeOrderCompleted
                && matchedOrderId == other.matchedOrderId
                && matchedOrderUid == other.matchedOrderUid
                && matchedOrderCompleted == other.matchedOrderCompleted
                && price == other.price
                && size == other.size
                && bidderHoldPrice == other.bidderHoldPrice
                && ((nextEvent == null && other.nextEvent == null) || (nextEvent != null && nextEvent.equals(other.nextEvent)));
    }


    /**
     * Includes chaining events
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                section,
                activeOrderCompleted,
                matchedOrderId,
                matchedOrderUid,
                matchedOrderCompleted,
                price,
                size,
                bidderHoldPrice,
                nextEvent);
    }


    @Override
    public String toString() {
        return "MatcherTradeEvent{" +
                "eventType=" + eventType +
                ", section=" + section +
                ", activeOrderCompleted=" + activeOrderCompleted +
                ", matchedOrderId=" + matchedOrderId +
                ", matchedOrderUid=" + matchedOrderUid +
                ", matchedOrderCompleted=" + matchedOrderCompleted +
                ", price=" + price +
                ", size=" + size +
                ", bidderHoldPrice=" + bidderHoldPrice +
                ", nextEvent=" + (nextEvent != null) +
                '}';
    }


}
