<div dir="rtl">

# EffectDroid

[English](README.md) | **فارسی**

**EffectDroid** ([`Idea-atmosphere/EffectDroid`](https://github.com/Idea-atmosphere/EffectDroid))
نسخه‌ی متن‌باز اندروید ویرایشگر موشن‌گرافیک و کامپوزیت دسکتاپ [EffectCraft](https://github.com/storytold/effectcraft)
(نوشته‌شده با Rust خالص) است. موتور Rust، گراف افکت‌ها و رابط egui همان کد نسخه‌ی دسکتاپ هستند؛
آنچه تغییر می‌کند پوسته و چیدمان موبایل است. نسخه‌ی upstream با `EFFECTCRAFT_REF` **پین** شده
(هم‌اکنون **v0.17.0**، تگ `362474a`) و در زمان ساخت پچ می‌شود — هرگز فورک نمی‌شود
(بخش [Upstream و پچ‌ها](#upstream-و-پچها) را ببینید).

> **وضعیت:** هر دو pipeline در CI با هر push ساخته می‌شوند — بسته‌ی wasm و APK پورت وب، و CLI +
> APK آزمایشی GUI پورت نیتیو. چیدمان موبایل با بازخورد دستگاه تکرار می‌شود (Home، نوار ابزار،
> منوها، دیالوگ‌ها، حرکات لمسی)؛ هر نکته‌ی باقی‌مانده در [docs/ANDROID.md](docs/ANDROID.md)
> → *Limitations* آمده است.

| پورت | آرتیفکت | وضعیت |
|---|---|---|
| وب → اندروید (APK مبتنی بر WebView دور بسته‌ی wasm رسمی، رابط کامل، آفلاین) | `effectdroid-web-v0.17.0` + APK بخش `webapp` | ✅ اصلی |
| لینوکس arm64 → اندروید arm64 (`effectcraft-cli` + APK رابط eframe) | `effectcraft-cli-android-arm64`، `effectdroid-native-apk` | ✅ CLI / 🧪 GUI |

> هیچ ارتباطی با EffectCraft یا ArtCraft ندارد. همه‌چیز روی دستگاه اجرا می‌شود؛ پروژه‌ها و رندرها
> هرگز از دستگاه خارج نمی‌شوند.

## ساخته‌شده برای گوشی

رابط دسکتاپ برای پنجره‌ای حدود ۱۴۰۰ نقطه طراحی شده؛ در عرض ۳۹۰–۴۳۰ نقطه هر سطح آن تطبیق
می‌یابد، بر اساس همان روشی که [PhotoDroid](https://github.com/Idea-atmosphere/PhotoDroid)
برای PhotoCraft به کار می‌برد:

- **فضای کاری Phone بر اساس هندسه.** پنجره‌ای که **کوتاه‌تر ضلعش زیر ۶۰۰ نقطه** باشد — در هر
  جهت‌گیری، تا گوشی افقی هم شامل شود — روی فضای کاری *Phone* باز می‌شود: نوار تب تمام‌عرض بالای
  تایم‌لاین که همه‌ی پنل‌ها از تب‌ها و منوی overflow آن‌ها در دسترس‌اند. Window ▸ Workspace مثل
  هر فضای کاری داخلی دیگری «Phone» را دارد؛ چیدمانی که کاربر جابه‌جا کرده باشد هرگز جایگزین نمی‌شود.
- **نوار ابزار.** زیر ۹۶۰ نقطه، مواردی که منوی جایگزین دارند حذف می‌شوند (لینک‌ها → Help،
  انتخابگرهای ابزار → Properties، snapping → View، تب‌های فضای کاری → منوی `»` که همه را فهرست
  می‌کند) و زیر ۷۲۰ نقطه ابزارها در دو ردیف می‌نشینند تا در هیچ عرض یا جهت‌گیری همپوشانی نباشد.
- **عنوان منوها اسکرول می‌شود.** File … Window … Help در یک نوار اسکرول افقی‌اند؛ با کشیدن
  انگشت به مواردی که در ۳۹۰ نقطه جا نمی‌شوند می‌رسید. نوار با عرض دسکتاپ دقیقاً مثل قبل می‌نشیند.
- **صفحه‌ی Home.** کاشی‌های سریع‌شروع زیر ۴۸۰ نقطه به ۲×۲ می‌روند، فیلتر Recent با عرض کوچک
  می‌شود و «Clear list» جایی که با تیتر تداخل داشت پنهان می‌شود.
- **هدف‌های لمسی و متن.** هدف‌های ۴۴×۳۶ نقطه‌ای و فاصله‌گذاری بازتر روی گوشی؛ UI Scale پیش‌فرض
  (۱۰۰) روی گوشی ×۱.۱۵ زوم می‌گیرد تا متن خوانا بماند — انتخاب صریح از Settings ▸ Appearance
  همچنان مقدم است.
- **حرکات، نه دابل‌کلیک.** نگه‌داشتن انگشت کار دابل‌کلیک را انجام می‌دهد — اعمال پریست، باز کردن
  کامپوزیشن، ویرایش نظر، تغییر نام، پرش به مارکر — برای هشت عملیاتی که قبلاً به دابل‌کلیک نیاز
  داشتند؛ و ردیف‌های لیست (افکت‌ها، پریست‌ها، مدیا، پروژه) روی تاچ فقط click حس می‌کنند تا کشیدن
  انگشت لیست را اسکرول کند نه ردیف را بکشد. ردیف‌هایی که منوی context دارند همان را نگه می‌دارند:
  نگه‌داشتن انگشت آنجا منو را باز می‌کند، مثل هر لیست اندرویدی.
- **Settings در اندازه‌ی صفحه.** دیالوگ‌ها از قبل به اندازه‌ی پنجره محدود می‌شوند (`modal_space`)؛
  روی گوشی صفحات Settings به نوار تب افقی بالای فیلدها تبدیل می‌شوند (به‌جای sidebar دسکتاپ) و
  دکمه‌های Previous/Next پایین صفحه در هر دو حالت کار می‌کنند.
- **نوارهای سیستم.** هر دو تم از edge-to-edge اجباری اندروید ۱۵ (targetSdk 35) صرف‌نظر می‌کنند
  تا رابط زیر نوار وضعیت/ناوبری نرود. خط لوله‌ی window-insets سبک PhotoDroid (ناحیه‌ی امن،
  بریدگی صفحه، کیبورد) کار آینده است — [docs/ANDROID.md](docs/ANDROID.md).

تبلت‌ها و پنجره‌هایی که کوتاه‌تر ضلعشان ≥ ۶۰۰ نقطه است چیدمان دسکتاپ را نگه می‌دارند. صفحه‌کلید/
ماوس مجازی وجود ندارد (PhotoDroid ‏`virtual_input` / `virtual_mouse` دارد)؛ فیلدهای متنی به
کیبورد سیستم تکیه می‌کنند.

## دریافت APK

1. **Actions → [Android (web port)](.github/workflows/android-web.yml) → آخرین اجرا →
   Artifacts** — بسته را دانلود و APK را با `adb install` نصب کنید.
2. ⚠️ اسم آرتیفکت‌ها بین اجراها **ثابت** است — همیشه از **آخرین** اجرای سبز بگیرید؛ دانلود قدیمی
   بuild قدیمی نشان می‌دهد.
3. ساخت‌های tag‌دار (`v*`) APK را به release همین مخزن متصل می‌کنند.

دستگاه: اندروید ۸ به بالا، WebView به‌روز (WebGPU یا WebGL2)، حدود ۲۵۰ مگابایت فضای آزاد.

## ساخت محلی

```sh
# پورت وب (JDK 17 + Android SDK؛ ابزارهای wasm برای build-web.sh)
scripts/build-web.sh                 # سورس + پچ‌های موبایل — یا: fetch-web-assets.sh (بسته‌ی stock)
cd android && ./gradlew :webapp:assembleRelease

# پورت نیتیو arm64 (اضافه: rustup target aarch64-linux-android، NDK 27.3.13750724)
scripts/build-native.sh all
```

## ساختار مخزن

| مسیر | توضیح |
|---|---|
| `patches/` | `0001-mobile-responsive-ui.patch` — کل تطبیق موبایل، ‏`-p1` روی تگ پین‌شده، قبل از هر ساخت روی چکاوت دست‌نخورده اعمال می‌شود |
| `scripts/` | `build-web.sh`، `fetch-web-assets.sh`، `apply-upstream-patches.sh` (تکرارپذیر با بررسی معکوس)، `build-native.sh` (cargo-ndk) |
| `android/webapp` | پوسته‌ی WebView (asset loader روی `https://appassets.androidplatform.net`، پل دانلود) |
| `android/nativeapp` | APK میزبانی‌کننده‌ی ساخت نیتیو aarch64-linux-android |
| `native/` | crate ورودی اندروید به‌صورت drop-in برای workspace پین‌شده‌ی upstream (هرگز فورک) |
| `docs/ANDROID.md` | یادداشت‌های کامل پورت: معماری، CI، امضای دستگاه، نیازمندی‌ها، محدودیت‌ها، فرایند ارتقا |
| `.github/workflows/` | `android-web.yml` + `android-native.yml` — هر دو با هر push و روی کل diff پریک اجرا می‌شوند |

## Upstream و پچ‌ها

- **پین‌شده، نه فورک.** `EFFECTCRAFT_REF` (پیش‌فرض `v0.17.0`) تگ upstream را انتخاب می‌کند؛ CI و
  ساخت محلی دقیقاً همان ref را با `git clone --depth 1 --branch` می‌گیرند و هرگز سراغ `main`
  upstream نمی‌روند.
- **یک پچ همه‌ی تغییرات UI اندروید را حمل می‌کند:**
  [`patches/0001-mobile-responsive-ui.patch`](patches/0001-mobile-responsive-ui.patch) (فضای
  کاری Phone، تطبیق نوار ابزار/منو/Home، اندازه‌های لمسی، زوم ۱.۱۵× گوشی، جایگزین‌های long-press
  و تب‌های Settings). ‏`scripts/apply-upstream-patches.sh` آن را روی درخت دست‌نخورده اعمال می‌کند
  و تکرارپذیر است — در اجرا دوم با بررسی معکوس رد می‌شود.
- **برای ویرایش:** تگ را clone کنید، اسکریپت اعمال را بزنید، درخت را ویرایش کنید و سپس از همان
  چکاوت با `git diff > patches/0001-mobile-responsive-ui.patch` بازتولید کنید (گام‌به‌گام در
  [docs/ANDROID.md](docs/ANDROID.md)).
- چیز دیگری vendored نیست: نه crate کپی‌شده، نه درخت همگام‌شده.

## مستندات

- **[docs/ANDROID.md](docs/ANDROID.md)** — معماری هر دو پورت، CI، امضا، محدودیت‌ها، نحوه‌ی ارتقای
  نسخه‌ی پین‌شده‌ی EffectCraft و بازتولید پچ.
- مستندات upstream: [storytold/effectcraft](https://github.com/storytold/effectcraft)
  (`docs/web.md`، `packaging/web/README.md`).
- پورت خواهر: [PhotoDroid](https://github.com/Idea-atmosphere/PhotoDroid) (PhotoCraft روی
  اندروید) — مرجع چیدمان موبایلی که اینجا استفاده شده است.

## لایسنس

مثل upstream یعنی **MIT OR Apache-2.0**، به انتخاب شما — [LICENSE-MIT](LICENSE-MIT)،
[LICENSE-APACHE](LICENSE-APACHE) و [NOTICE](NOTICE) را ببینید.
دارایی‌های شخص ثالثی که بسته‌ها حمل می‌کنند (فونت‌ها، آیکون‌ها، تصاویر برنامه) مجوزهای خودشان را
دارند و فهرست تک‌فایلی‌شان در [ATTRIBUTION.md](ATTRIBUTION.md) آمده — از upstream EffectCraft
آورده شده است.

</div>
