package net.wrlu.forensictool.backup;

import android.os.Build;

import java.util.Arrays;
import java.util.List;

public final class VendorRegistry {

    public static final String HONOR = "honor";
    public static final String OPPO = "oppo";
    public static final String XIAOMI = "xiaomi";
    public static final String VIVO = "vivo";

    static final Entry[] ENTRIES = {
        new Entry(HONOR,  "com.hihonor.android.clone",   Arrays.asList("honor")),
        new Entry(OPPO,   "com.coloros.backuprestore",   Arrays.asList("oppo", "oneplus", "realme")),
        new Entry(XIAOMI, "com.miui.huanji",             Arrays.asList("xiaomi", "redmi", "poco")),
        new Entry(VIVO,   "com.vivo.easyshare",          Arrays.asList("vivo", "iqoo")),
    };

    public static String detectVendorByBrand() {
        String brand = Build.BRAND.toLowerCase();
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        for (Entry e : ENTRIES) {
            for (String keyword : e.brandKeywords) {
                if (brand.contains(keyword) || manufacturer.contains(keyword)) {
                    return e.vendor;
                }
            }
        }
        return null;
    }

    public static String getServerPackage(String vendor) {
        if (vendor == null) return null;
        for (Entry e : ENTRIES) {
            if (e.vendor.equals(vendor)) {
                return e.serverPackage;
            }
        }
        return null;
    }

    static final class Entry {
        final String vendor;
        final String serverPackage;
        final List<String> brandKeywords;

        Entry(String vendor, String serverPackage, List<String> brandKeywords) {
            this.vendor = vendor;
            this.serverPackage = serverPackage;
            this.brandKeywords = brandKeywords;
        }
    }

    private VendorRegistry() {}
}