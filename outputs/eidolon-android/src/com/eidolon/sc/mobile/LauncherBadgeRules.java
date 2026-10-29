package com.eidolon.sc.mobile;
final class LauncherBadgeRules {
    static boolean vivo(String maker,String brand){return matches(maker)||matches(brand);}
    private static boolean matches(String value){return value!=null&&(value.trim().equalsIgnoreCase("vivo")||value.trim().equalsIgnoreCase("iqoo"));}
    static int count(int unread,boolean notifications){return notifications?Math.max(0,unread):0;}
    private LauncherBadgeRules(){}
}
