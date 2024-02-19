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
package exchange.core2.core.utils;

import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.openhft.chronicle.bytes.*;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.WireType;
import org.eclipse.collections.api.map.primitive.MutableIntLongMap;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public final class SerializationUtils {


    public static long[] bytesToLongArray(final NativeBytes<Void> bytes, final int padding) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate((int) bytes.readRemaining());
        bytes.read(byteBuffer);
        final byte[] array = byteBuffer.array();
//        log.debug("array:{}", array);
        final long[] longs = toLongsArray(array, padding);
//        log.debug("longs:{}", longs);
        return longs;
    }

    /**
     * Nén mảng bytes thành 1 mảng long sử dụng Lz4
     * Mảng long đầu ra phải đáp ứng yêu cầu là chứa trọn vẹn dữ liệu mảng bytes và là bội số của "padding"
     */
    public static long[] bytesToLongArrayLz4(final LZ4Compressor lz4Compressor, final NativeBytes<Void> bytes, final int padding) {
        // lấy kích thước có thể đọc của mảng byte đầu vào
        int originalSize = (int) bytes.readRemaining();
//        log.debug("COMPRESS originalSize={}", originalSize);

        // tạo 1 ByteBuffer với size bằng bytes truyền vào
        final ByteBuffer byteBuffer = ByteBuffer.allocate(originalSize);
        // convert bytes truyền vào qua bytes mới
        bytes.read(byteBuffer);
        // đặt lại limit của bytes về đúng giá trị cuối cùng có thể --> đọc mảng bytes thuận tiện hơn
        byteBuffer.flip();

        // Tạo ra 1 mảng byte chứa kết quả sau khi nén
        // 4 byte đầu tiên chứa độ dài data
        final ByteBuffer byteBufferCompressed = ByteBuffer.allocate(4 + lz4Compressor.maxCompressedLength(originalSize));
        byteBufferCompressed.putInt(originalSize);// override with compressed length
        lz4Compressor.compress(byteBuffer, byteBufferCompressed);
        // giảm limit của mảng byte về đúng limit thực tế
        byteBufferCompressed.flip();

        int compressedBytesLen = byteBufferCompressed.remaining();

        return toLongsArray(
                byteBufferCompressed.array(),       // các phần tử trong byte_buffer đổi thành array
                byteBufferCompressed.arrayOffset(),     // lấy vị trí có giá trị đầu tiên trong mảng byte. Ví dụ mảng byte có 100 phần tử, nhưng từ phần tử thứ 2 trở đi mới có giá trị thì offset của nó = 2;
                compressedBytesLen,
                padding);
    }


    public static long[] toLongsArray(final byte[] bytes, final int padding) {

        final int longLength = requiredLongArraySize(bytes.length, padding);
        long[] longArray = new long[longLength];
        //log.debug("byte[{}]={}", bytes.length, bytes);
        final ByteBuffer allocate = ByteBuffer.allocate(longLength * 8 * 2);
        final LongBuffer longBuffer = allocate.asLongBuffer();
        allocate.put(bytes);
        longBuffer.get(longArray);
        return longArray;
    }

    /**
     * Từ bytes[] đầu vào convert thành long[], với điều kiện độ dài mảng long phải chứa đủ mảng bytes và chia hết cho padding
     */
    public static long[] toLongsArray(final byte[] bytes, final int offset, final int length, final int padding) {
        // tính toán độ dài long array tối thiểu để chứa mảng bytes có độ dài "length" và phải chia hết cho "padding"
        final int longLength = requiredLongArraySize(length, padding);
        // tạo 1 long array với độ dài tính toán phía trên
        long[] longArray = new long[longLength];
        //log.debug("byte[{}]={}", bytes.length, bytes);
        // Tạo 1 ByteBuffer với độ dài x2 so với cần thiết (có vẻ để chống tràn bộ đệm nữa)
        final ByteBuffer allocate = ByteBuffer.allocate(longLength * 8 * 2);
        final LongBuffer longBuffer = allocate.asLongBuffer();
        // ghi mảng bytes đầu vào vào ByteBuffer allocate
        allocate.put(bytes, offset, length);
        // chuyển longBuffer sang longArray
        longBuffer.get(longArray);
        return longArray;
    }


    /**
     * Hàm này lấy kích cỡ của mảng long cần để chứa mảng bytes có "bytesLength" phần tử
     * Kích cỡ mảng long đầu ra phải là bội số của "padding"
     * Ví dụ bytesLength = 100;
     *      requiredLongArraySize(100) = 13
     *      rem = 13 % 4 = 1
     *      len output = 13 + (4 - 1) = 16
     */
    public static int requiredLongArraySize(final int bytesLength, final int padding) {
        int len = requiredLongArraySize(bytesLength);
        if (padding == 1) {
            return len;
        } else {
            int rem = len % padding;
            return rem == 0 ? len : (len + padding - rem);
        }
    }


    public static Wire longsToWire(long[] dataArray) {

        final int sizeInBytes = dataArray.length * 8;
        final ByteBuffer byteBuffer = ByteBuffer.allocate(sizeInBytes);
        byteBuffer.asLongBuffer().put(dataArray);

        final byte[] bytesArray = new byte[sizeInBytes];
        byteBuffer.get(bytesArray);

        //log.debug(" section {} -> {}", section, bytes);

        final Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(sizeInBytes);
        bytes.ensureCapacity(sizeInBytes);

        bytes.write(bytesArray);

        return WireType.RAW.apply(bytes);
    }

    public static Wire longsLz4ToWire(long[] dataArray, int longsTransfered) {

//        log.debug("long dataArray.len={} longsTransfered={}", dataArray.length, longsTransfered);

        final ByteBuffer byteBuffer = ByteBuffer.allocate(longsTransfered * 8);
        byteBuffer.asLongBuffer().put(dataArray, 0, longsTransfered);

        final int originalSizeBytes = byteBuffer.getInt();

        final ByteBuffer uncompressedByteBuffer = ByteBuffer.allocate(originalSizeBytes);

        final LZ4FastDecompressor lz4FastDecompressor = LZ4Factory.fastestInstance().fastDecompressor();

        lz4FastDecompressor.decompress(byteBuffer, byteBuffer.position(), uncompressedByteBuffer, uncompressedByteBuffer.position(), originalSizeBytes);

        final Bytes<ByteBuffer> bytes = Bytes.wrapForRead(uncompressedByteBuffer);

        return WireType.RAW.apply(bytes);
    }

    /**
     * Xác định chính xác số lượng phần tử tối thiểu trong long array để chứa hết mảng bytes có 'bytesLength' phần tử
     * vì kiểu long có kích cỡ 8 byte --> số phần tử bytes / 8 --> tương đương dịch phải 3 bit
     * "bytesLength - 1" là để đảm bảo nếu số lượng bytesLength chia hết cho 8 thì nó sẽ ko bị quá thêm 1 bit
     *      ví dụ: bytesLength = 7 --> cần 1 bit, bytesLength = 9 --> cần 1 bit thôi chứ ko phải 2
     *      đoạn "+ 1" là để đảm bảo có 1 phần tử long chứa lượng bytes dư ra
     */
    public static int requiredLongArraySize(final int bytesLength) {
        return ((bytesLength - 1) >> 3) + 1;
    }

    public static void marshallBitSet(final BitSet bitSet, final BytesOut bytes) {
        marshallLongArray(bitSet.toLongArray(), bytes);
    }

    public static BitSet readBitSet(final BytesIn bytes) {
        // TODO use LongBuffer
        return BitSet.valueOf(readLongArray(bytes));
    }


    public static void marshallLongArray(final long[] longs, final BytesOut bytes) {
        bytes.writeInt(longs.length);
        for (long word : longs) {
            bytes.writeLong(word);
        }
    }


    public static long[] readLongArray(final BytesIn bytes) {
        final int length = bytes.readInt();
        final long[] array = new long[length];
        // TODO read byte[], then convert into long[]
        for (int i = 0; i < length; i++) {
            array[i] = bytes.readLong();
        }
        return array;
    }

    public static void marshallLongIntHashMap(final MutableLongIntMap hashMap, final BytesOut bytes) {

        bytes.writeInt(hashMap.size());
        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeLong(k);
            bytes.writeInt(v);
        });
    }

    public static LongIntHashMap readLongIntHashMap(final BytesIn bytes) {
        int length = bytes.readInt();
        final LongIntHashMap hashMap = new LongIntHashMap(length);
        // TODO shuffle (? performance can be reduced if populating linearly)
        for (int i = 0; i < length; i++) {
            long k = bytes.readLong();
            int v = bytes.readInt();
            hashMap.put(k, v);
        }
        return hashMap;
    }

    public static void marshallIntLongHashMap(final MutableIntLongMap hashMap, final BytesOut bytes) {

        bytes.writeInt(hashMap.size());

        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeInt(k);
            bytes.writeLong(v);
        });
    }

    public static IntLongHashMap readIntLongHashMap(final BytesIn bytes) {
        int length = bytes.readInt();
        final IntLongHashMap hashMap = new IntLongHashMap(length);
        // TODO shuffle (? performance can be reduced if populating linearly)
        for (int i = 0; i < length; i++) {
            int k = bytes.readInt();
            long v = bytes.readLong();
            hashMap.put(k, v);
        }
        return hashMap;
    }


    public static void marshallLongHashSet(final LongHashSet set, final BytesOut bytes) {
        bytes.writeInt(set.size());
        set.forEach(bytes::writeLong);
    }

    public static LongHashSet readLongHashSet(final BytesIn bytes) {
        int length = bytes.readInt();
        final LongHashSet set = new LongHashSet(length);
        // TODO shuffle (? performance can be reduced if populating linearly)
        for (int i = 0; i < length; i++) {
            set.add(bytes.readLong());
        }
        return set;
    }


    public static <T extends WriteBytesMarshallable> void marshallLongHashMap(final LongObjectHashMap<T> hashMap, final BytesOut bytes) {

        bytes.writeInt(hashMap.size());

        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeLong(k);
            v.writeMarshallable(bytes);
        });

    }

    public static <T> void marshallLongHashMap(final LongObjectHashMap<T> hashMap, final BiConsumer<T, BytesOut> valuesMarshaller, final BytesOut bytes) {

        bytes.writeInt(hashMap.size());

        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeLong(k);
            valuesMarshaller.accept(v, bytes);
        });

    }

    public static <T> LongObjectHashMap<T> readLongHashMap(final BytesIn bytes, final Function<BytesIn, T> creator) {
        int length = bytes.readInt();
        final LongObjectHashMap<T> hashMap = new LongObjectHashMap<>(length);
        for (int i = 0; i < length; i++) {
            hashMap.put(bytes.readLong(), creator.apply(bytes));
        }
        return hashMap;
    }

    public static <T extends WriteBytesMarshallable> void marshallIntHashMap(final IntObjectHashMap<T> hashMap, final BytesOut bytes) {
        bytes.writeInt(hashMap.size());
        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeInt(k);
            v.writeMarshallable(bytes);
        });
    }

    public static <T> void marshallIntHashMap(final IntObjectHashMap<T> hashMap, final BytesOut bytes, final Consumer<T> elementMarshaller) {
        bytes.writeInt(hashMap.size());
        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeInt(k);
            elementMarshaller.accept(v);
        });
    }


    public static <T> IntObjectHashMap<T> readIntHashMap(final BytesIn bytes, final Function<BytesIn, T> creator) {
        int length = bytes.readInt();
        final IntObjectHashMap<T> hashMap = new IntObjectHashMap<>(length);
        for (int i = 0; i < length; i++) {
            hashMap.put(bytes.readInt(), creator.apply(bytes));
        }
        return hashMap;
    }


    public static <T extends WriteBytesMarshallable> void marshallLongMap(final Map<Long, T> map, final BytesOut bytes) {
        bytes.writeInt(map.size());

        map.forEach((k, v) -> {
            bytes.writeLong(k);
            v.writeMarshallable(bytes);
        });
    }

    public static <T, M extends Map<Long, T>> M readLongMap(final BytesIn bytes, final Supplier<M> mapSupplier, final Function<BytesIn, T> creator) {
        int length = bytes.readInt();
        final M map = mapSupplier.get();
        for (int i = 0; i < length; i++) {
            map.put(bytes.readLong(), creator.apply(bytes));
        }
        return map;
    }

    public static <K, V> void marshallGenericMap(final Map<K, V> map,
                                                 final BytesOut bytes,
                                                 final BiConsumer<BytesOut, K> keyMarshaller,
                                                 final BiConsumer<BytesOut, V> valMarshaller) {
        bytes.writeInt(map.size());

        map.forEach((k, v) -> {
            keyMarshaller.accept(bytes, k);
            valMarshaller.accept(bytes, v);
        });
    }

    public static <K, V, M extends Map<K, V>> M readGenericMap(final BytesIn bytes,
                                                               final Supplier<M> mapSupplier,
                                                               final Function<BytesIn, K> keyCreator,
                                                               final Function<BytesIn, V> valCreator) {
        int length = bytes.readInt();
        final M map = mapSupplier.get();
        for (int i = 0; i < length; i++) {
            map.put(keyCreator.apply(bytes), valCreator.apply(bytes));
        }
        return map;
    }

    public static <T extends WriteBytesMarshallable> void marshallList(final List<T> list, final BytesOut bytes) {
        bytes.writeInt(list.size());
        list.forEach(v -> v.writeMarshallable(bytes));
    }

    public static <T> List<T> readList(final BytesIn bytes, final Function<BytesIn, T> creator) {
        final int length = bytes.readInt();
        final List<T> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(creator.apply(bytes));
        }
        return list;
    }

    public static <T> void marshallNullable(final T object, final BytesOut bytes, final BiConsumer<T, BytesOut> marshaller) {
        bytes.writeBoolean(object != null);
        if (object != null) {
            marshaller.accept(object, bytes);
        }
    }

    public static <T> T preferNotNull(final T a, final T b) {
        return a == null ? b : a;
    }

    public static <T> T readNullable(final BytesIn bytesIn, final Function<BytesIn, T> creator) {
        return bytesIn.readBoolean() ? creator.apply(bytesIn) : null;
    }

    public static <V> LongObjectHashMap<V> mergeOverride(final LongObjectHashMap<V> a, final LongObjectHashMap<V> b) {
        final LongObjectHashMap<V> res = a == null ? new LongObjectHashMap<>() : new LongObjectHashMap<>(a);
        if (b != null) {
            res.putAll(b);
        }
        return res;
    }

    public static <V> IntObjectHashMap<V> mergeOverride(final IntObjectHashMap<V> a, final IntObjectHashMap<V> b) {
        final IntObjectHashMap<V> res = a == null ? new IntObjectHashMap<>() : new IntObjectHashMap<>(a);
        if (b != null) {
            res.putAll(b);
        }
        return res;
    }

    public static IntLongHashMap mergeSum(final IntLongHashMap... maps) {
        IntLongHashMap res = null;
        for (IntLongHashMap map : maps) {
            if (map != null) {
                if (res == null) {
                    res = new IntLongHashMap(map);
                } else {
                    map.forEachKeyValue(res::addToValue);
                }
            }
        }
        return res != null ? res : new IntLongHashMap();
    }

}
