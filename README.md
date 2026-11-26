# WynnLootrun Advisor 1.0.7

**[Tải mod WynnLootrun Advisor 1.0.7 (.jar)](https://github.com/minhtang2005/wynnlootrun-advisor/releases/download/v1.0.7/WynnLootrunAdvisor-1.0.7.jar)**

Mod Fabric phía máy khách cho Minecraft 1.21.11, Fabric API 0.141.3+1.21.11, Wynntils 4.2.18 hoặc 4.2.19 và Java 21. Mod đọc dữ liệu hiện trên client, tính điểm cục bộ và hiển thị lời khuyên trong Minecraft. Mọi thao tác gameplay do người chơi thực hiện.

## Cài đặt trong Modrinth

Dùng hồ sơ Minecraft 1.21.11 Fabric với Fabric Loader từ 0.18.4, đúng Fabric API 0.141.3+1.21.11 và Wynntils 4.2.18 hoặc 4.2.19. Chép `WynnLootrunAdvisor-1.0.7.jar` vào thư mục `mods` của hồ sơ. Nếu đã cài Advisor, thay file cũ bằng file mới. Xem bản Wynntils trong danh sách mod hoặc log khởi động vì tên file có thể khác phiên bản bên trong. [Wynntils 4.2.19](https://github.com/Wynntils/Wynntils/releases/tag/v4.2.19)

Khi đang ở trong world, bấm **F8** để mở Advisor. Chọn **Kiếm tiền** để dùng chiến lược Cashout, xả kho và ưu tiên End Reward Reroll. Chọn **Nuôi Sac** để dùng Sacrifice Stack, ưu tiên tích Pulls xuyên run và khóa All In. Chế độ được lưu và áp dụng ngay. Lần cài mới mặc định chọn Kiếm tiền. Có thể đổi phím trong **Options → Controls → Key Binds → WynnLootrun Advisor**. Phím mở Advisor từ màn chơi để tránh đóng menu server đang mở.

HUD xuất hiện trong Lootrun và các màn hình lựa chọn/phần thưởng mà bản đọc nhận diện được. Tên lựa chọn giữ nguyên tiếng Anh để khớp với game. Các lệnh dưới đây chạy cục bộ trong client. Dùng Tab để xem tùy chọn.

Bấm **F8 → Di chuyển HUD** để mở trình chỉnh overlay của Wynntils và chọn sẵn bảng **WynnLootrun Advisor**. Kéo thân bảng để đổi vị trí, kéo cạnh để đổi kích thước, rồi bấm **Apply** để lưu. Bảng cũng có trong trình chỉnh overlay chung của Wynntils. Vị trí này áp dụng cho HUD trong world. Bảng hướng dẫn khi mở menu nhiệm vụ vẫn hiện cạnh menu.

| Lệnh | Tác dụng |
| --- | --- |
| `/wla goal max_pull` | Tối đa Pulls |
| `/wla goal sacrifice_stack` | Tích Pulls xuyên run |
| `/wla goal cashout` | Tối đa giá trị xả kho |
| `/wla goal flying_chest` | Ưu tiên Flying Chests |
| `/wla camp auto` | Nhận diện camp gần điểm bắt đầu hoặc để trạng thái chưa rõ |
| `/wla camp fruma_west` | Chọn camp nếu đã vào giữa run hoặc nhận diện không chắc |
| `/wla details` | Xem hạng lựa chọn hiện có, các đóng góp từng trục, combo và cảnh báo |
| `/wla weight 7 1.5` | Chỉnh một trục trong khoảng 0–2 |
| `/wla weight reset` | Khôi phục trọng số |
| `/wla position 10 52` | Đổi vị trí HUD theo tọa độ giao diện |
| `/wla scale 0.85` | Đổi tỷ lệ HUD trong khoảng 0.75–1.5 |
| `/wla toggle` | Bật hoặc tắt HUD và thu thập trạng thái |

Thứ tự tám trục là Raw Pulls, End Reroll, End Sacrifice, Setup & Pool Window, Run Longevity, Flying Chest, Survival & Cleanse và Objective Rush. Cấu hình mục tiêu và trọng số ở `config/wynnlootrun-advisor.properties`. Wynntils lưu vị trí và kích thước HUD cùng các overlay khác.

## Nguồn và logic

Ba tài liệu patch 2.2.6_4 là nguồn của trị số và danh mục.

- `wynncraft_lootrun_master_guide_max_pull_playbook_patch_2_2_6_4.md` cho combo, xác suất phần thưởng và chiến lược tích kho.
- `wynncraft_lootrun_complete_context_patch_2_2_6_4.md` cho danh mục, tỷ lệ theo tier, điều kiện mở và Base EV.
- `k_ho_ch_logic_h_th_ng_tr_ng_s_ng_lootrun_patch_2_2_6_4.md` cho trọng số động, ngưỡng reroll và Rush/Delay.

`lootrun-rules.tsv` chứa 33 mission, 14 trial và 17 boon. `Engine.java` có 14 beacon, 8 trục, bộ lọc, mức điểm, kiểm tra combo, xếp hạng Dark Grey, Rush/Delay, Beacon Reroll và quyết định phần thưởng. Kill Run cần mọi combo phù hợp với mục tiêu đều bất khả thi, danh sách offer đủ, không còn nguồn cứu run hoặc reroll, trạng thái đã xác nhận và giá trị phần thưởng thấp. Khi chưa đủ dữ liệu, mod giữ nguyên lựa chọn tiếp tục và cho biết các giá trị còn thiếu.

Từ 1.0.1, tất cả challenge/beacon, mission, trial, boon và lựa chọn xóa mission đã nhận diện đều đi qua đánh giá combo. Điểm combo dùng chung điều kiện khả thi với Kill Run. Mod cộng điểm cho việc lấy mảnh ghép còn thiếu, mở thêm lượt chọn hoặc dùng hiệu ứng đã ghép. Điểm combo bổ sung từ 4 đến 13, được giảm một nửa khi dữ liệu chưa đủ xác nhận, và trừ 12 nếu xóa một mission còn phục vụ combo. Đây là hệ số triển khai bổ sung cho trọng số động và synergy đã định nghĩa trong tài liệu. HUD hiện lý do của lựa chọn khuyến nghị. `/wla details` hiện lý do cho từng lựa chọn được xếp hạng.

Từ 1.0.2, HUD đọc bảng Lootrun Minecraft đã nhận để xác định lúc chọn beacon, số challenge và thời gian, kể cả khi trạng thái Wynntils chưa cập nhật. Tên challenge đọc từ bảng giữ cách viết hoa gốc. Dòng bước tiếp theo tự xuống hàng nếu HUD hẹp.

Từ 1.0.3, dòng bước tiếp theo và màu tô trên HUD dùng chung quyết định sau Rush/Delay. Khi menu mission, trial, boon hoặc xóa mission mở, ô được khuyên chọn có viền xanh nếu đúng item vẫn nằm trong ô. Khi đầu vào chấm điểm giữ nguyên, mod tái sử dụng kết quả và phần bố trí chữ. HUD báo số beacon đã đọc và mục còn thiếu khi có dữ liệu chưa đầy đủ.

Từ 1.0.5, các ô cùng tên mission, trial hoặc boon được gộp thành một lựa chọn. Advisor ưu tiên ô có hướng dẫn bấm chọn, kiểm tra lại tên item trước khi tô viền, và vẽ viền bao các ô cùng tên nằm gần nhau trong cùng cột. Lệnh `/wla details` hiện ID các ô khi một thẻ chiếm nhiều ô.

Từ 1.0.6, Advisor đọc các tên beacon từ thông báo hệ thống sau dòng **Choose a Beacon!** và ghép theo màu với marker và dữ liệu thử thách đã nhận. Beacon chỉ xuất hiện trong chat vẫn được chấm điểm theo màu. Tên challenge, tọa độ và khoảng cách để trống khi chưa đọc được. Danh sách chat được làm mới ở mỗi lần chọn beacon.

Từ 1.0.7, khi đã đọc đủ sáu lựa chọn, Advisor ưu tiên các màu hợp lệ khác. Khi đọc được ít hơn sáu, Orange trở lại cách chấm điểm bình thường. Nếu số lựa chọn chưa rõ, Advisor giảm ưu tiên Orange khi Wynntils ghi nhận từ hai Orange đang hoạt động và hiệu ứng hết hạn sớm nhất còn hơn ba challenge. Orange vẫn là lựa chọn dự phòng nếu các màu khác không đủ điều kiện.

Bản 1.0.3 đối chiếu giao diện với clip Molten Lootrun. Phần đọc chat nhận số reroll dưới dòng bấm reroll. Dữ liệu sidebar nhận objective dạng `6/13 Beacons`, `0/10 Challenges` và `12.5/18m`. Mod nhận ra bước mở Challenge Rewards giữa các lượt. Rương thưởng cuối run vẫn yêu cầu xác nhận nội dung riêng trước khi khuyên reroll hay sacrifice. [Mission và beacon 2:58](https://www.youtube.com/watch?v=J2mH-naGLLI&t=178s) · [Trial và Challenge Rewards 18:20](https://www.youtube.com/watch?v=J2mH-naGLLI&t=1100s)

Các quyết định triển khai và phần cần đối chiếu thêm:

- Tài liệu ghi đơn vị ePull tương đương và Base EV nhưng chưa cho hệ số quy đổi đầy đủ cho từng mục. Mod dùng hệ số cố định, có thể đọc trong `Engine.java`, cho sản lượng từng trục, dự báo giá trị nhận được và ước tính tiến độ combat/chest. Khoảng cách ảnh hưởng nhẹ khi Wynntils cung cấp. Đây là điểm tư vấn, không phải xác suất thắng challenge hoặc xác suất ra beacon.
- Mốc Green là 210/330/450/570 giây theo bảng context. Silent Expanse dùng 10% HP/challenge theo bảng camp. Hai tuyến Canyon có cùng tọa độ camp và cùng thông số trong tài liệu, do đó chế độ tự động dùng bộ hệ số chung.
- Advisor đọc trạng thái, timer, challenge, vị trí camp, lịch sử beacon/mission, tier Vibrant, số lượt Orange/Rainbow và dự đoán tên thử thách từ các model Wynntils. Nếu thiếu tên dự đoán, Advisor dùng tên hiện trên bảng hoặc loại và khoảng cách từ marker hiển thị của Minecraft. Wynntils 4.2.19 có hỗ trợ màu Violet và marker mới.
- Advisor đọc objective đang chạy qua model Mission và Trial mới. Mod cũng dùng tên hiển thị, scoreboard, thông báo hệ thống và lore của menu. Trial được bắt đầu và trial đã hoàn thành được lưu riêng. Nếu server chưa hiển thị kết quả hoặc tiến độ theo định dạng đọc được, trạng thái vẫn là chưa xác nhận.
- Tiến độ objective, reroll beacon, Pulls hiện tại, kho Pulls, boon, curse và các bộ đếm cộng dồn phụ thuộc vào dữ liệu hiện rõ trên giao diện hoặc đã quan sát trong run. Vào giữa run có thể thiếu lịch sử. UI dùng `?` khi chưa biết; điểm có thể dùng giả định trung tính. Nếu chưa đọc được nội dung rương đáng tin cậy, mod chỉ gợi ý có điều kiện. Kill Run bị chặn khi dữ liệu quyết định thiếu.
- Tính tương thích được đối chiếu với các interface của Wynntils 4.2.18 và 4.2.19, Fabric API và mapping Mojang cho Minecraft 1.21.11. Chưa chạy một Lootrun thật trên Wynncraft. Định dạng menu/lore ở server, chuyển tiếp beacon sau reroll và hiển thị HUD với modpack của bạn vẫn cần kiểm thử trong game.

## Quy định Wynncraft

Game Rule 1 cấm mod tạo lợi thế gameplay. Danh sách ví dụ của Wynncraft không bao quát mọi tính năng. Bản này chỉ dùng trạng thái client đang hiển thị hoặc dữ liệu từ các marker/dự đoán Wynntils, và không thực hiện gameplay. Chưa có xác nhận của moderator về hợp lệ của tư vấn chiến thuật thời gian thực. Hãy hỏi moderator trước khi dùng trên server. [Quy định Wynncraft và phần Modifications](https://forums.wynncraft.com/threads/game-forum-rules.111874/)

## Biên dịch

Dùng JDK 21, sau đó chạy `gradlew.bat build` trên Windows hoặc `sh gradlew build` trên macOS/Linux. Gradle sẽ tải dependency đã ghim. Tác vụ `deterministicTest` kiểm thử toàn bộ danh mục, điều kiện mở, tier, kinh tế, đọc menu, Rush/Delay và các điều kiện chặn Kill Run. File mod đã remap ở `build/libs/WynnLootrunAdvisor-1.0.7.jar`.

Tác vụ `runClientGameTest` khởi chạy client Minecraft cùng Fabric API và Wynntils trong thư mục thử nghiệm riêng. World cục bộ gửi bảng Lootrun, marker, dòng chat beacon và số reroll, cùng các menu có ô lựa chọn qua kết nối Minecraft. Bài kiểm tra tái hiện dòng chat Purple/Blue khi client chỉ nhận một marker, kéo và lưu HUD trong Wynntils, kiểm tra màu viền đúng ô trên ảnh và xác nhận mod tái sử dụng kết quả nếu dữ liệu giữ nguyên. Mặc định chạy với 4.2.19. Dùng `-PwynntilsArtifact=rFyKV858` để thử lại với 4.2.18. Cần xác minh tiếp trong một Lootrun thật để kiểm tra thứ tự gói tin, menu và lore trên Wynncraft. [Fabric Client Game Tests](https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.11/develop/automatic-testing.md)

[Wynntils 4.2.19](https://github.com/Wynntils/Wynntils/tree/v4.2.19) · [Fabric API 0.141.3+1.21.11](https://maven.fabricmc.net/docs/fabric-api-0.141.3%2B1.21.11/) · [Fabric cho Minecraft 1.21.11](https://fabricmc.net/2025/12/05/12111.html)
