# OBD Panel (Android)

ELM327 (Classic Bluetooth/SPP) adaptöre bağlanıp motor verilerini ve arıza
kodlarını gösteren native Android uygulaması. Root gerekmez.

## Nasıl derlenir (GitHub Actions ile, kurulum gerektirmez)

Bu projede `.github/workflows/build.yml` hazır — bir GitHub reposuna
yüklediğinde otomatik olarak APK derler.

1. github.com'da oturum aç, sağ üstten **+ > New repository** ile yeni
   (boş) bir repo oluştur, adını istersen `obd-panel` yap, **Public**
   veya **Private** fark etmez.
2. Repo sayfasında **Add file > Upload files**'a tıkla.
3. Bilgisayarındaki `OBDDash` klasörünün **içindeki tüm dosya ve alt
   klasörleri** (klasörün kendisini değil, içeriğini) bu sayfaya
   sürükle-bırak yap. Tarayıcın klasör yapısını korumazsa, en kolay
   yol **GitHub Desktop** uygulamasını (ücretsiz, komut satırı
   gerektirmez) kurup projeyi oradan yüklemek.
4. Alt tarafta **Commit changes**'e bas.
5. Üstteki **Actions** sekmesine geç — "APK Derle" adında bir işin
   otomatik başladığını göreceksin (birkaç dakika sürer).
6. İş bittiğinde (yeşil tik ✓) o işin sayfasına gir, en altta
   **Artifacts** bölümünde `obd-panel-debug-apk` dosyasını indir.
   Bu bir `.zip` olarak iner, içinden `app-debug.apk` çıkar.
7. `app-debug.apk`'yı telefonuna aktar (WhatsApp'a kendine gönder,
   Drive'a koy, kablo vs.), telefonda dosyaya dokun, kur. İlk seferde
   "bilinmeyen kaynaklardan yükleme"ye izin vermen istenebilir.

## Nasıl derlenir (Android Studio ile, alternatif)

1. Android Studio'yu aç (yoksa: https://developer.android.com/studio).
2. **File > Open** ile bu klasörü (`OBDDash`) seç.
3. İlk açılışta Gradle senkronizasyonu birkaç dakika sürebilir; Android
   Studio Gradle wrapper'ı kendisi indirip kuracaktır ("Install Gradle
   wrapper?" gibi bir uyarı çıkarsa Kabul et / OK de).
4. Üstte cihaz seçiciden telefonunu (USB ile bağlı, USB hata ayıklama
   açık) ya da bir emülatör seç.
5. Yeşil "Run" (▶) butonuna bas.

USB kablon yoksa: **Build > Build Bundle(s)/APK(s) > Build APK(s)**
diyerek bir `.apk` üretip telefonuna kopyalayabilir, oradan kurabilirsin
(Ayarlar'dan "bilinmeyen kaynaklardan yükleme"yi bir kerelik açman
gerekebilir).

## Kullanmadan önce

ELM327'yi **telefonun kendi Bluetooth ayarlarından** normal şekilde
eşleştir (PIN genelde `1234` veya `0000`). Uygulama sadece zaten
eşleşmiş cihazları listeler, kendi başına eşleştirme yapmaz.

## Kullanım

1. Uygulamayı aç, ilk açılışta Bluetooth izni isteyecek, onayla.
2. "Eşleşmiş cihazları yenile"ye bas, listede ELM327'yi gör.
3. Yanındaki "Bağlan"a bas.
4. Bağlantı kurulunca gösterge paneli otomatik dolmaya başlar.
5. "Kodları Temizle" arıza kodlarını (mode 04) siler.

## Nasıl çalışıyor

- `ObdBluetoothManager.kt`: `BluetoothSocket` ile ELM327'ye standart
  SPP UUID'siyle (`00001101-0000-1000-8000-00805F9B34FB`) bağlanır,
  AT komutlarıyla başlatır, ardından standart mod 01 PID'lerini
  (RPM, hız, motor sıcaklığı, gaz kelebeği, motor yükü, emiş sıcaklığı,
  yakıt seviyesi, voltaj) ve mod 03/04 (DTC oku/temizle) sırayla sorgular.
- `WebAppInterface.kt`: WebView içindeki JS'in `Android.connect(...)`
  gibi çağrılar yapabilmesini sağlayan köprü.
- `assets/dashboard.html`: Arayüzün tamamı - Android'den gelen JSON'u
  `window.onData(...)` ile alıp göstergeleri günceller.

## Sorun giderme

- Cihaz listesi boşsa: önce sistem Bluetooth ayarlarından eşleştir.
- Bağlantı sürekli hata veriyorsa: adaptörü çıkarıp takmayı (güç
  döngüsü) dene, ardından tekrar "Bağlan"a bas.
- Bazı çok ucuz klonlarda ilk `connect()` başarısız olabiliyor;
  kod otomatik olarak yansıma (reflection) ile kanal 1'i deniyor,
  yine de olmazsa adaptörün gerçekten OBD2 destekli olduğundan
  emin ol (kontak açık, motor kodları destekleyen bir araç).
