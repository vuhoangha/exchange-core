## [AGRONA](https://github.com/real-logic/agrona)
- Chứa các class, function Util giúp tối ưu performance.
- Các class có chức năng chính tương tự như Java nhưng có hiệu suất tốt hơn (MutableBoolean, MutableLong...vv)
###### MutableBoolean
- Class Boolean chỉ có "get", ko có "set" --> change data phải tạo lại 1 object mới.
- AtomicBoolean tuy có thể change được nhưng phải lock --> performance thấp hơn
- MutableBoolean có thể change value trực tiếp --> tiết kiệm CPU, giảm tải GC.
- MutableLong tương tự MutableBoolean
- Có thể sử dụng trong Runnable, Lambda Expression vì nó có thể dùng biến Final, change giá trị qua hàm

## JAVA
###### BiConsumer
- Là 1 functional interface nhận 2 biến đầu vào và ko return.
- ObjLongConsumer: nhận 2 giá trị đầu vào (Class<T>, long)

###### [Serializable](https://topdev.vn/blog/noi-ve-serialization-trong-java/)
- Dùng để chuyển đổi Object data sang 1 dạng khác như Text/Binary nhằm lưu trữ file, write memory hoặc gửi qua mạng
- Giả sử muốn ghi Object vào file, phải implement interface "Serializable" trong class của Object. Nếu ko thì quá trình ghi file sẽ exception
- ["serialVersionUID"](https://shareprogramming.net/serialversionuid-la-gi/) dùng để đánh dấu version của class. Các class khác nhau có thể chung 1 version. Khi 1 Object được serializable thì nó sẽ kèm version, và muốn Deserialization thì phải cùng version như trên
- Được [khuyến nghị](https://stackoverflow.com/a/53889695) ko nên dùng. 1 số data schema thay thế như Protobuf, JSON, XML...vv

###### [Thread Safe](https://vncoder.vn/bai-viet/thread-safety-luong-an-toan-trong-java-va-dong-bo-hoa-synchronized-cac-luong-trong-java)
- Khi 2 Thread cùng đọc và ghi 1 biến thì có thể kết quả ko chính xác. Ví dụ 2 thread cùng increment 1 var thì kết quả cuối cùng sẽ ko chuẩn
- Thread safe là làm cho process luôn hoạt động chính xác trong đa luồng

##### [Functional Interface](https://gpcoder.com/3869-functional-interface-trong-java-8/)
- Chỉ chứa duy nhất 1 method trừu tượng
- Tính năng hữu ích nhất là có thể dùng Lambda Expression
- Có thể dùng như 1 Object để truyền như 1 tham số vào hàm
###### Example:
```java
public class Main {
    public static void main(String[] args) {
        System.out.println(run(Main::cal));
    }
    public static int cal(int x) {
        return x * x;
    }
    public static int run(TestFunctional s) {
        return s.doSomething(12);
    }

    @FunctionalInterface
    interface TestFunctional {
        int doSomething(int index);
    }
}
- ```

##### [POJO](https://viblo.asia/p/tight-coupling-and-loose-coupling-between-java-objects-aRBvXWdakWE)
- Là 1 class Java thuần ko extend/implement các library/framework khác
- Các class/interface kế thừa 1 POJO thì cũng được coi là POJO

##### [REFLECTION](https://topdev.vn/blog/java-reflection-la-gi-huong-dan-java-reflection-api/)
- Có thể lấy class_name, thuộc package nào, field gì, method nào...của 1 object, kể cả ko biết nó thuộc class nào
- Có thể truy cập private field, thay đổi giá trị final field trong quá trình runtime
- Hiệu năng thấp hơn vì phải quét classpath để xác định class nào
- [Tham khảo](https://viblo.asia/p/java-va-nhung-dieu-thu-vi-co-the-ban-chua-biet-LzD5dJeOZjY)

##### [UNSAFE](https://viblo.asia/p/java-va-nhung-dieu-thu-vi-co-the-ban-chua-biet-LzD5dJeOZjY)
- [Tham khảo](https://blogs.oracle.com/javamagazine/post/the-unsafe-class-unsafe-at-any-speed)
- Phá vỡ tất cả các rule thông thường của Java vì nó tương tác trực tiếp với memory, CPU...vv
- Chính vì tương tác thẳng với CPU, memory nên hiệu suất của nó rất tốt --> các thư viện vẫn dùng
- Khó debug, tìm lỗi hơn

##### [MEMCPY](https://www.scaler.com/topics/memcpy-in-c/)
- [Tài liệu](https://stackoverflow.com/a/19687987)
- Một method của C để copy dữ liệu từ địa chỉ bộ nhớ biến A -> B, có performance rất tốt

##### [VOLATILE](https://viblo.asia/p/java-volatile-Ljy5VXLVZra)
- Đánh dấu 1 biến được lưu trong bộ nhớ chính, mọi hành động đọc/ghi sẽ thực hiện trên bộ nhớ chính
- Các thread tương tác với biến này sẽ lấy giá trị của nó từ bộ nhớ chính

##### Lock Reentrance & Wait & Notify
- Lock Reentrance: lock 1 Thread lại để xử lý và unlock thủ công
- Wait & Notify: Khi 1 Thread muốn lock nhưng đã có Thread khác lock rồi thì nó phải call hàm Wait() để chờ. Khi Thread khác unlock nó sẽ call Notify()/NotifyAll() để báo cho các Thread đang Wait biết để thức dậy
- [Example](https://shareprogramming.net/thread-wait-notify-notifyall-trong-java/)

##### [Supplier](https://gpcoder.com/3967-supplier-trong-java-8/)
- Là Functional Interface ko có tham số và chỉ trả về 1 giá trị
- Có các kiểu cho dữ liệu nguyên thủy "BooleanSupplier", "IntSupplier", "LongSupplier", "DoubleSupplier"

##### [TreeMap](https://www.geeksforgeeks.org/internal-working-of-treemap-in-java/)
- Áp dụng thuật toán của [cây đỏ-đen](https://blog.luyencode.net/cay-do-den-red-black-tree-phan-1/)
- Có thể custom sort các key
- [Hiệu năng thấp khi so sánh với các loại Map khác](https://www.javamadesoeasy.com/2015/04/hashmap-vs-hashtable-vs-linkedhashmap.html#:~:text=LinkedHashMap%20maintains%20insertion%20order%20in,order%20of%20keys%20in%20java.&text=HashMap%20is%20not%20synchronized%2C%20hence,are%20slower%20as%20compared%20HashMap.)
- Key-Value ko được null

##### [LinkedHashmap](https://gpcoder.com/2672-lop-linkedhashmap-trong-java/)
- Cho phép key/value null
- Duy trì các phần tử theo thứ tự chèn

##### [DTO](https://viblo.asia/p/entity-domain-model-va-dto-sao-nhieu-qua-vay-YWOZroMPlQ0)
- DTO(Data transfer object) dùng để đóng gói data chuyển giữa client-server hoặc giữa các service trong microservice
- Giảm bớt lượng info ko cần thiết khi lấy từ DB, làm cho data gọn nhẹ, bảo mật

##### [Singleton](https://topdev.vn/blog/design-pattern-series-gioi-thieu-singleton/)
- Là 1 design pattern đảm bảo 1 class chỉ có duy nhất 1 instance
- Có thể call instance này từ bất cứ đâu

##### [Object Pool](https://topdev.vn/blog/huong-dan-java-design-pattern-object-pool/)
- Tạo pool chứa tập hợp các object có thể tái sử dụng
- Connection Pool, Thread Pool cũng dựa trên pattern này

##### [Mechanical Sympathy](https://batnamv.medium.com/t%C3%ACm-hi%E1%BB%83u-v%E1%BB%81-kh%C3%A1i-ni%E1%BB%87m-mechanical-sympathy-v%C3%A0-b%E1%BB%99-th%C6%B0-vi%E1%BB%87n-lmax-disruptor-4d553dc7fa55)
- Lập trình phần mềm đồng điệu với phần cứng

##### JIT
- "Just-In-Time" Compiler giúp biên dịch bytecode thành mã máy cho chương trình cụ thể đang chạy. (Bytecode là mã trung gian do Java Compiler tạo ra từ mã nguồn Java)
- Biên Dịch Tại Thời Điểm Chạy (Runtime Compilation): Khác với biên dịch tĩnh (static compilation), JIT biên dịch bytecode khi ứng dụng đang chạy, giúp JVM tối ưu hiệu suất chương trình bằng cách xem xét dữ liệu và hoạt động thực tế của ứng dụng.
- JIT tập trung vào các phần mã được thực thi nhiều nhất, tối ưu hóa và caching nó
- Bytecode Java độc lập với nền tảng, có thể chạy trên bất cứ máy nào có JVM. JIT chuyển bytecode ra mã máy tương thích với phần cứng cụ thể

##### Phép toán nhị phân
- Tìm số dư với điều kiện số bị chia có dạng 2^n
```java
public class Main {
    public static int tim_so_du_thong_thuong(int a, int b) {
        return a % b;
    }

    public static int tim_so_du_nhi_phan(int a, int b) {
        return a & (b-1);
        // giải thích logic chỗ này
        // số 9: 1001
        // số 3: 0011
        // khi sử dụng toán tử '&' kết quả sẽ = 1 chỉ khi cả 2 bit đều là 1
        // do đó phép '&' phía trên sẽ cắt được 2 bit '10' ngay đầu số 9 (thực ra 2 bit này chính là kết quả của phép chia bình thường '10' = 2)
        // từ đó chừa lại phần dư. Và phép toán '&' của phần dư với số (2^n-1) sẽ luôn bằng chính phần dư đó --> kết quả 
    }
}
- ```
- Nhân 2^n hoặc chia 2^n
```java
public class Main {
    public static int nhan_2_mu_n_thong_thuong(int a, int exponent) {
        return a * Math.pow(2, exponent);
    }

    public static int nhan_2_mu_n_nhi_phan(int a, int exponent) {
        return a << exponent;
        // giải thích logic chỗ này
        // dịch 1 bit tương đương x2
        // số 9:  01001
        // số 18: 10010
        // khi 1 bit được dịch sang bên trái 1 lần tương đương giá trị của chính bit ấy x2
        // ví dụ '10' thì bit '1' lúc này có giá trị '2^1'
        // dịch 1 bit sang trái thì thành '100' thì bit '1' lúc này sẽ là '2^2' --> tăng x2
        // giá trị 1 số biểu diễn dạng nhị phân như sau = a * 2^n + b * 2^(n-1) + c * 2^(n-2)
        // khi dịch sang bên trái 1 bit thì đồng nghĩa tất cả các bit đều x2 giá trị so với chính nó
        // từ đó giá trị của 1 số x2
        // logic này cũng tương tự khi ta dịch 'n' bit thì giá trị số đó sẽ tăng/giảm 2^n lần tùy thuộc vào hướng trái/phải
    }
}
- ```
- Log cơ số 2 (log2) hoặc tìm 'n' để 2^n = x
```java
public class Main {
    // bản chất của hàm này chỉ là chia 2 cho tới khi nào số đầu vào == 0
    // mỗi lần /2 và giá trị sau đó > 0 thì sẽ được tính là 1 lần
    // nếu 1 số /2 được 3 lần rồi mới đạt giá trị 0 thì chứng tỏ logarit cơ số 2 của số đó (log2) = 3
    // ở đây chia 2 nó sẽ ko chia thẳng mà nó dịch sang phải 1 bit
    public static int log2(int i) {
        int r;
        for (r = 0; (i >>= 1) != 0; ++r) {
        }
        return r;
    }
}
- ```

##### Instruction Reordering & Cache in Threads (Thread Caching)
- Instruction Reordering: là quá trình mà trong đó trình biên dịch (Compiler) hoặc CPU sắp xếp lại thứ tự của các hướng dẫn (instructions) để tối ưu hóa hiệu suất. Quá trình này có thể xảy ra tại một số mức độ khác nhau, từ trình biên dịch nguồn (source compiler) đến Just-In-Time Compiler (JIT) của JVM, và cả ở mức độ phần cứng (CPU). Quá trình này có thể gây ra các vấn đề liên quan đến tính nhất quán và visibility của dữ liệu giữa các luồng, vì các luồng khác nhau có thể "nhìn thấy" các thay đổi trong thứ tự khác nhau.
```java
public class Main {
    public static int a = 1;
    public static int b = 2;
    public static void main() {
        a = 11;
        b = 12;
        // các thread khác nhau có thể nhìn thấy "a = 11;" trước hoặc "b = 12;" trước
        // do cách trình biên dịch, JIT hoặc CPU sắp xếp
    }
}
- ```
- Cache in Threads (Thread Caching)
  - Mỗi luồng trong Java có thể có bản sao cục bộ của một số biến trong cache của CPU. Điều này giúp tăng tốc độ truy cập dữ liệu nhưng cũng có thể dẫn đến vấn đề về visibility, khi một luồng cập nhật một biến mà các luồng khác không biết.
  - Trong Java, việc sử dụng từ khóa volatile với một biến giúp đảm bảo rằng mọi đọc và viết vào biến đó đều được thực hiện trực tiếp trên bộ nhớ chính, giảm thiểu vấn đề visibility do thread caching gây ra.
- Các post liên quan
  - [Java-instruction-reordering-cache-in-threads](https://stackoverflow.com/questions/14321212/java-instruction-reordering-cache-in-threads)
  - [A Faster Volatile](https://robsjava.blogspot.com/2013/06/a-faster-volatile.html?view=sidebar)

## [CHRONICLE WIRE](https://github.com/OpenHFT/Chronicle-Wire)
- [Tài liệu 1](https://dzone.com/articles/high-performance-java-serialization-to-different-f)
- [Tài liệu 2](https://dzone.com/articles/did-you-know-the-fastest-way-of-serializing-a-java)
- [Tài liệu 3](https://dzone.com/articles/how-to-get-c-speed-in-java-serialisation)
- [Tài liệu 4](https://chronicle.software/chronicle-wire-object-marshalling/)
- Chuyển data từ "string" sang "long" nhằm giảm số byte sử dụng
- Field name, data type đã được khai báo sẵn, Java ko cần reflection để tìm class_name, field type nữa nên hiệu năng tốt hơn
- Sử dụng thêm UNSAFE và memcpy để truy cập trực tiếp tới vùng nhớ của biến --> có thể đọc cùng lúc nhiều field được(bulk operator) thay vì phải đọc tuần tự từng field
- Tóm lại là Seriabled dữ liệu nhanh hơn, nhỏ gọn hơn, đỡ tốn CPU hơn
- Khi write theo thứ tự nào, kiểu dữ liệu gì thì khi đọc cũng phải theo thứ tự đó
    - Ví dụ
    - ```java
    // write
    BytesOut bytes;
    bytes.writeLong(price_in);
    SerializationUtils.marshallLongMap(entries_in, bytes);
    bytes.writeLong(totalVolume_in);
    
    // read
    price_out = bytes.readLong();
    entries_out = SerializationUtils.readLongMap(bytes, LinkedHashMap::new, Order::new);
    totalVolume_out = bytes.readLong();
    - ```

## BUSINESS LOGIC
#### Cách tính amount, price, internal price, price step...vv trong hệ thống
- [Tài liệu 1](https://github.com/exchange-core/exchange-core/issues/85)
- [Tài liệu 2](https://github.com/exchange-core/exchange-core/issues/96)
- [Tài liệu 3](https://github.com/exchange-core/exchange-core/issues/54)
- baseScaleK: số lượng satoshi trong 1 lot. Giả sử 1 BTC = 10^8 satoshi, 1 lot = 10^6 satoshi --> 1 lot = 0.01 BTC.
- quoteScaleK: số lượng litoshi trong 1 price step.
- price_step: đơn vị nhỏ nhất để tính toán trong hệ thống. Hệ thống luôn tính toán với số nguyên và đơn vị nhỏ nhất của số nguyên là 1, tương ứng với 1 price step.
- internal_price: giá dùng để tính toán trong hệ thống. Có thể hiểu nó là số lượng price_step.
- takerFee: taker fee in litoshi.
- makerFee: maker fee in litoshi.
###### Ví dụ
Giao dịch BTC/USDT
- baseCurrency: "BTC" (1 BTC = 10^8 satoshi)
- quoteCurrency: "USDT" (1 USDT = 10^8 litoshi)
- baseScaleK: 1_000_000 satoshi
- quoteScaleK: 1_000 litoshi
- takerFee: 1_900 litoshi (trên 1 lot)
- makerFee: 700 litoshi (trên 1 lot)
- price BTC/USDT = 30_000.32 USDT
    - --> Giá 1 lot = (30_000.32 * 10^6 / 10^8) = 300.0032 USDT = 30_000_320_000 litoshi
    - --> Giá internal = 30_000_320_000 / 1000 = 30_000_320
    - Giá internal chính là số lượng price_step
#### Statehash
- Có vẻ mục đích là để lấy trạng thái hiện tại của 1 object. Vì object hash bởi các field nên nếu field giống nhau sẽ luôn cho ra statehash giống nhau

## [ECLIPSE COLLECTIONS](https://github.com/eclipse/eclipse-collections)
- Bộ thư viện chuyên về sử dụng List, Set, Map, HashMap...vvv
- Tối ưu hóa performance và các hàm Util cho tiện lợi
- Tham khảo:
    - [Giới thiệu về các tính năng thư viện](https://betterprogramming.pub/rich-lazy-mutable-and-immutable-interfaces-in-eclipse-collections-ce64a31b5936)
    - [So sánh Collections vs Eclipse Collections](https://www.baeldung.com/jdk-collections-vs-eclipse-collections#:~:text=Accordingly%20to%20our%20tests%2C%20Eclipse,JDK%20running%20also%20in%20parallel.)
    - [UnifiedSet — Trình tiết kiệm bộ nhớ](https://medium.com/oracledevs/unifiedset-the-memory-saver-25b830745959)
    - [Ten reasons to use Eclipse Collections](https://medium.com/oracledevs/ten-reasons-to-use-eclipse-collections-91593104af9d)
    - [Sơ đồ chức năng trong Eclipse Collections](https://medium.com/oracledevs/visualizing-eclipse-collections-646dad9533a9)

## [LMAX DISRUPTOR](https://github.com/LMAX-Exchange/disruptor)
- [Example 1](https://lmax-exchange.github.io/disruptor/user-guide/index.html)
- [Wait Strategies](https://lmax-exchange.github.io/disruptor/user-guide/index.html#_alternative_wait_strategies)
    - SleepingWaitStrategy
        - Sử dụng 1 vòng lặp đơn giản, sử dụng LockSupport.parkNanos(1) trong vòng lặp để pause Thread lại 1 khoảng thời gian
        - Độ trễ trung bình để giữa producer và consumer sẽ cao hơn
        - Hoạt động tốt nhất trong tình huống ko yêu cầu dộ trễ thấp cũng như ít tác động tới CPU. Thường dùng cho ghi log ko đồng bộ.
    - YieldingWaitStrategy
        - Chiến thuật giống "SleepingWaitStrategy" nhưng có độ trễ thấp hơn. Ring_Buffer sử dụng hàm Thread.yield() để khi rảnh rỗi sẽ nhường cho Thread khác chạy
        - Phù hợp khi cần high performance và low latency
        - Số Thread của Consumer nên nhỏ hơn logical cores của CPU (Hyper Threading)
    - BusySpinWaitStrategy
        - Tốc độ thực thi nhanh nhất trong các "Wait Strategies" nhưng cũng gây stress lên CPU nhất
        - Nên dùng khi số Thread Consumer < số lượng lõi vật lý (physical cores) CPU
    - BlockingWaitStrategy
        - Đây là mặc định của "Wait Strategies"
        - Nó dùng ReentrantLock & Wait & Notify để xử lý
        - Đây là cách xử lý chậm nhất vì phải Locking nhưng sẽ an toàn, thân thiện với CPU nhưng đổi lại là tốc độ thực thi chậm nhất.
        - Phù hợp cho hệ thống có CPU thấp, thường xuyên qúa tải và ko yêu cầu low latency.

## [LZ4](https://github.com/lz4/lz4-java)
- Là 1 thư viện dùng để nén data có tốc độ nhanh và kích cỡ nhỏ
- Có thể áp dụng để nén dữ liệu trước khi lưu vào DB hoặc cache server (Redis)

## [Adaptive Radix Tree](https://db.in.tum.de/~leis/papers/ART.pdf)
- Là 1 cây nhị phân hỗ trợ lưu in-memory và truy vấn nhanh hơn
- Tốc độ của nó nhanh tương tự HashTable nhưng ưu việt hơn là có thể sort key trong khi HashTable thì ko
- Luợng memory nó cần và độ phức tạp của Tree cũng giảm
- Tài liệu tham khảo
    - [Tài liệu 1](https://medium.com/nlnetlabs/adapting-radix-trees-15fe7d27c894)
    - [Tài liệu 2](https://medium.com/techlog/how-i-implemented-an-art-adaptive-radix-trie-data-structure-in-go-to-increase-the-performance-of-a8a2300b246a)
    - [Tối ưu hóa cho việc Index trong Database](https://www.reddit.com/r/golang/comments/ti6f42/how_i_implemented_an_art_adaptive_radix_trie_data/)

## [OpenHFT Java-Thread-Affinity](https://github.com/OpenHFT/Java-Thread-Affinity)
- Binding 1 Thread vào 1 CPU Core
- Lợi ích:
    - nhằm giảm context switch, chỉ định 1 thread chỉ chạy trên 1 core, ko phải switch qua các CPU khác
- Nhược điểm:
    - Có 1 số thread dùng lâu quá, dẫn tới Core bị block, các task sau sẽ phải chờ lâu hơn
- [Tài liệu 1](https://www.sciencedirect.com/topics/computer-science/thread-affinity#:~:text=Thread%20affinity%20provides%20a%20way,where%20they%20need%20to%20be.)
- [Tài liệu 2](https://en.wikipedia.org/wiki/Processor_affinity)

## [Chronicle Queue](https://github.com/OpenHFT/Chronicle-Queue)
- [Viblo](https://viblo.asia/tags/chronicle-queue)
- [VOZ](https://voz.vn/t/nen-chon-message-queue-nao.242978/page-2)

## [Hamcrest Matchers](https://gpcoder.com/5292-junit-hamcrest-matchers/#:~:text=Hamcrest%20l%C3%A0%20m%E1%BB%99t%20th%C6%B0%20vi%E1%BB%87n,h%C6%A1n%20v%C3%A0%20d%E1%BB%85%20%C4%91%E1%BB%8Dc%20h%C6%A1n.)
- Dùng để check xem 1 kết quả đầu ra có trùng khớp kết quả mong muốn ko
- Gộp nhiều điều kiện lại để kiểm tra cùng lúc có khớp nhau ko hoặc 1 trong các điều kiện khớp nhau
- Có thể tùy chỉnh show ra thông tin mô tả testcase

## Suggestion
- Sử dụng [Chronicle-Queue](https://github.com/OpenHFT/Chronicle-Queue) để làm Message-Queue tương tự RabbitMQ, Kafka
- [Tài liệu](https://drive.google.com/file/d/1_Qiwj7s8QyIF6LojbpCyUqkPWCgjtHUP/view?usp=sharing) tham khảo exchange-core được soạn từ admin của project
- [Lý do tại sao ko dùng DB trong hệ thống mà chỉ dùng RAM](https://t.me/exchangecoretalks/7524)
    - "to be able to do that you use event sourcing"
- Tham khảo project exchange này "https://github.com/Polygant/OpenCEX"
- Hiểu tường tận về các thành phần trong project (Telegram: "can u explain me what does this mean ?")
- Tham khảo các loại [Message Queue](https://voz.vn/t/nen-chon-message-queue-nao.242978/page-2)

## Thiết kế hệ thống & Pattern
- [Các vấn đề quan tâm khi thiết kế hệ thống](https://batnamv.medium.com/system-design-c%C6%A1-b%E1%BA%A3n-v%E1%BB%81-thi%E1%BA%BFt-k%E1%BA%BF-h%E1%BB%87-th%E1%BB%91ng-218cb6059e2a)
- DDD(Domain Driven Design)
    - [Tài liệu VOZ](https://voz.vn/t/domain-driven-design-with-cqrs-event-sourcing.575269/)
- Event Sourcing
    - [Tài liệu Techmaster](https://techmaster.vn/posts/34863/event-sourcing-xay-dung-event-storage)
    - [Grokking Vietnam](https://www.facebook.com/grokking.vietnam/posts/1907182842683008/)
    - [Viblo](https://viblo.asia/p/event-sourcing-la-gi-gwd43zDXVX9)
    - [Microsoft](https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing)
    - [Microsoft Introduce Event Sourcing](https://learn.microsoft.com/en-us/previous-versions/msp-n-p/jj591559(v=pandp.10))
    - [CQRS and Event Sourcing](https://learn.microsoft.com/en-us/previous-versions/msp-n-p/jj591577(v=pandp.10))
    - [Khóa học Udemy](https://fpt-software.udemy.com/course/java-microservices-cqrs-event-sourcing-with-kafka/learn/lecture/26390652#overview)
    - [Youtube Talk](https://www.youtube.com/watch?v=JHGkaShoyNs)
- CQRS Pattern(Command and Query Responsibility Segregation)
    - Chia riêng phần Command(write) và Query(read) thành các module/service với DB độc lập
    - Mục đích nhằm tối ưu hóa riêng từng phần và scale từng phần dễ dàng
    - [Tài liệu Topdev](https://topdev.vn/blog/cqrs-pattern-la-gi-vi-du-de-hieu-ve-cqrs-pattern/)
    - [Tài liệu Greg Young - CQRS and Event Sourcing](https://www.youtube.com/watch?v=JHGkaShoyNs)

## Memory Barrier
##### StoreStore Barrier
```java
// đảm bảo các lệnh ghi trước barrier được thực thi trước khi các lệnh ghi sau barrier được thực thi
public class StoreStoreBarrierExample {
    private int var1 = 0;
    private int var2 = 0;
    private int var3 = 0;
    private volatile boolean ready = false;

    // đảm bảo các lệnh ghi luôn được hoàn thành trước barrier rồi mới đến các lệnh ghi phía sau
    public void method() {
        // ghi giá trị vào các biến bình thường
        var1 = 1;
        var2 = 2;

        // đặt 1 barrier đảm bảo giá trị var1 và var2 đã được cập nhật
        ready = true;

        // cập nhật vào các biến bình thường sau khi update cho biến ready
        var3 = var1 + var2; // luôn luôn bằng 3
    }
}
```
##### StoreLoad Barrier
```java
// đảm bảo các lệnh ghi trước barrier được thực thi trước khi các lệnh đọc sau barrier
public class StoreLoadExample {
    private int nonVolatileData1 = 0;
    private int nonVolatileData2 = 0;
    private volatile int trigger = 0;

    public void writerThread() {
        nonVolatileData1 = 100; // Bước 1: ghi vào biến không volatile
        nonVolatileData2 = 200; // Bước 2: ghi vào biến không volatile khác

        trigger = 1; // Bước 3: ghi vào biến volatile, đặt StoreLoad Barrier ở đây
    }

    public void readerThread() {
        if (trigger == 1) { // Bước 4: đọc từ biến volatile, đảm bảo rằng các ghi trước đó được "nhìn thấy"
            System.out.println(nonVolatileData1); // Bước 5: đọc giá trị cập nhật, đảm bảo rằng bước 1 và 2 đã hoàn thành
            System.out.println(nonVolatileData2); // Bước 6: tương tự như trên
        }
    }
}
```
##### LoadStore Barrier
```java
// đảm bảo các lệnh đọc trước barrier được thực hiện rồi mới tới các lệnh ghi phía sau barrier
public class LoadStoreExample {
    private int var1 = 10;
    private int var2 = 20;
    private volatile int volatileVar = 0;

    public void updateVariables() {
        int tempVar1 = var1; // Bước 1: Đọc biến var1
        int tempVolatile = volatileVar; // Bước 2: Đọc từ biến volatile, đặt LoadStore Barrier ở đây
        var2 += tempVar1; // Bước 3: chỉ được thực thi sau khi đã đọc được giá trị var1
    }
}
```
##### LoadLoad Barrier
```java
// đảm bảo các lệnh đọc trước barrier được thực hiện rồi mới tới các lệnh đọc phía sau barrier
public class LoadLoadExample {
    private int var1 = 10;
    private int var2 = 20;
    private volatile int volatileVar = 0;

    public void updateVariables() {
        int tempVar1 = var1; // Bước 1: Đọc biến var1
        int tempVolatile = volatileVar; // Bước 2: Đọc từ biến volatile, đặt LoadLoad Barrier ở đây
        var2 = var1 + tempVar1; // Bước 3: chỉ được thực thi sau khi đã đọc được giá trị var1
    }
}
```