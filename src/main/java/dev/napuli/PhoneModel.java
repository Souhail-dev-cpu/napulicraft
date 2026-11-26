package dev.napuli;

/** Mainland configurations: the OS and hardware always refer to the same regional model. */
public enum PhoneModel {
    ONEPLUS_16("oneplus_16", "OnePlus 16", "ColorOS 17", "Snapdragon 8 Elite Gen 6", "Adreno 850", "16 GB LPDDR5X Ultra", "512 GB UFS 4.1", "6.78\" AMOLED · 2772×1272 · 165/185 Hz", 9000, "100 W", "200 + 50 + 50 MP · Front 50 MP", 0xffcdd0d5, "https://www.oneplus.com/cn/16/specs"),
    VIVO_X500_PRO_MAX("vivo_x500_pro_max", "vivo X500 Pro Max", "OriginOS 7", "Dimensity 9600 Pro", "Mali-G2-Ultra-NX", "16 GB LPDDR5X Ultra", "512 GB UFS 4.1", "6.85\" AMOLED · 3168×1440 · 144 Hz", 8000, "90 W / 40 W", "50 + 200 + 50 MP ZEISS · Front 50 MP", 0xff41464c, "https://www.vivo.com.cn/vivo/param/x500promax"),
    IQOO_15("iqoo_15", "iQOO 15", "OriginOS 6", "Snapdragon 8 Elite Gen 5", "Adreno 840", "16 GB LPDDR5X Ultra", "512 GB UFS 4.1", "6.85\" AMOLED · 3168×1440 · 144 Hz", 7000, "100 W / 40 W", "50 + 50 + 50 MP · Front 32 MP", 0xffe4e5e4, "https://www.vivo.com.cn/vivo/param/iQOO15"),
    IQOO_16("iqoo_16", "iQOO 16", "OriginOS 7", "Snapdragon 8 Elite Gen 6 + Q4", "Adreno", "16 GB LPDDR5X Ultra", "512 GB UFS 4.1", "6.85\" AMOLED · 3168×1440 · 165 Hz", 8400, "100 W / 40 W", "50 + 50 + 50 MP · Front 32 MP", 0xff575666, "https://www.vivo.com.cn/vivo/param/iqoo16"),
    ONEPLUS_15("oneplus_15", "OnePlus 15", "ColorOS 16", "Snapdragon 8 Elite Gen 5", "Adreno 840", "16 GB LPDDR5X Ultra", "512 GB UFS 4.1", "6.78\" AMOLED · 2772×1272 · 120/165 Hz", 7300, "120 W / 50 W", "50 + 50 + 50 MP · Front 32 MP", 0xff181a1e, "https://www.oneplus.com/cn/15");

    public final String id, name, os, cpu, gpu, ram, storage, display, charging, cameras, source;
    public final int battery, color;
    PhoneModel(String id, String name, String os, String cpu, String gpu, String ram, String storage, String display,
               int battery, String charging, String cameras, int color, String source) {
        this.id=id; this.name=name; this.os=os; this.cpu=cpu; this.gpu=gpu; this.ram=ram; this.storage=storage;
        this.display=display; this.battery=battery; this.charging=charging; this.cameras=cameras; this.color=color; this.source=source;
    }
    public boolean colorOS() { return os.startsWith("ColorOS"); }
}
