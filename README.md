# PictoPDF - Android App

一個Android應用程式，用於將多張照片壓縮後轉換成PDF檔案，並支援email分享功能。

## 功能特色

### 📸 多照片選擇
- 支援一次選擇多張照片
- 使用Android系統的圖片選擇器
- 支援常見的圖片格式（JPEG、PNG等）

### 🗜️ 智能壓縮
- 自動調整圖片大小（最大1200x1600像素）
- 根據EXIF資料自動旋轉圖片
- 優化的JPEG壓縮品質（80%）
- 保持圖片比例不變形

### 📄 PDF轉換
- 使用iText 7庫進行PDF生成
- 每張圖片自動適應頁面大小
- 保持圖片品質和比例
- 自動居中排版

### 📧 分享功能
- **Email分享**: 透過系統Email應用程式發送PDF
- **通用分享**: 支援任何可處理PDF檔案的應用程式
- 自動生成帶時間戳的檔案名稱

### 🔒 權限管理
- 自動處理Android 13+的照片存取權限
- 相容舊版Android的檔案存取權限
- 使用FileProvider安全分享檔案

## 系統需求

- **最低Android版本**: Android 5.0 (API 21)
- **目標Android版本**: Android 14 (API 34)
- **權限需求**:
  - `READ_EXTERNAL_STORAGE` (Android 12及以下)
  - `READ_MEDIA_IMAGES` (Android 13+)

## 建置說明

### 先決條件
- Android Studio 2022.3.1 或更新版本
- JDK 8 或更新版本
- Android SDK 34

### 建置步驟
1. 克隆專案到本地：
   ```bash
   git clone https://github.com/ChiaYing-Chen/Android_App_pictopdf.git
   ```

2. 使用Android Studio開啟專案

3. 等待Gradle同步完成

4. 連接Android裝置或啟動模擬器

5. 點擊「Run」按鈕或使用快捷鍵Ctrl+R (Windows/Linux) 或 Cmd+R (Mac)

### 命令列建置
```bash
# 建置Debug版本
./gradlew assembleDebug

# 建置Release版本
./gradlew assembleRelease

# 執行測試
./gradlew test
```

## 使用說明

1. **選擇照片**: 點擊「選擇照片」按鈕，從手機相簿中選擇多張照片
2. **預覽照片**: 選擇的照片會顯示在列表中，可以預覽縮圖
3. **轉換PDF**: 點擊「轉換為PDF」按鈕開始轉換過程
4. **分享PDF**: 轉換完成後，可以選擇透過Email或其他方式分享PDF檔案

## 技術架構

### 主要技術棧
- **Kotlin**: 主要開發語言
- **iText 7**: PDF生成庫
- **Glide**: 圖片載入和快取
- **EasyPermissions**: 權限管理
- **Coroutines**: 異步處理

### 專案結構
```
app/src/main/
├── java/com/chiaying/pictopdf/
│   ├── MainActivity.kt          # 主要活動
│   ├── ImageAdapter.kt          # 圖片列表適配器
│   ├── ImageCompressor.kt       # 圖片壓縮處理
│   └── PdfGenerator.kt          # PDF生成邏輯
├── res/
│   ├── layout/                  # 界面佈局檔案
│   ├── values/                  # 字串和樣式資源
│   └── xml/                     # 配置檔案
└── AndroidManifest.xml          # 應用程式清單
```

## 貢獻指南

歡迎提交Issue和Pull Request！

### 開發環境設置
1. Fork此專案
2. 建立功能分支：`git checkout -b feature/新功能`
3. 提交變更：`git commit -m '新增新功能'`
4. 推送到分支：`git push origin feature/新功能`
5. 提交Pull Request

## 授權條款

此專案採用MIT授權條款 - 詳見[LICENSE](LICENSE)檔案

## 版本歷史

### v1.0.0
- ✅ 多照片選擇功能
- ✅ 智能圖片壓縮
- ✅ PDF轉換功能
- ✅ Email分享功能
- ✅ 通用分享功能
- ✅ 中文界面支援
- ✅ Android 13+權限相容

## 聯絡資訊

如有問題或建議，請透過以下方式聯絡：
- 建立Issue：[GitHub Issues](https://github.com/ChiaYing-Chen/Android_App_pictopdf/issues)
- Email：[您的Email地址]

---

**PictoPDF** - 讓照片轉PDF變得簡單！ 📸➡️📄