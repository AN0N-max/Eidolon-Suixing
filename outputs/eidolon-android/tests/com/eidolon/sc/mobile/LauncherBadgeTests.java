package com.eidolon.sc.mobile;
public final class LauncherBadgeTests {
 static int n;static void check(boolean value,String name){if(!value)throw new AssertionError(name);n++;System.out.println("PASS "+name);}
 public static void main(String[] args){
  check(LauncherBadgeRules.vivo("vivo","vivo"),"Vivo bridge selected");
  check(LauncherBadgeRules.vivo("vivo","iQOO"),"iQOO with Vivo manufacturer supported");
  check(LauncherBadgeRules.vivo("unknown"," IQOO "),"iQOO brand case and whitespace supported");
  check(!LauncherBadgeRules.vivo("Samsung","Samsung")&&!LauncherBadgeRules.vivo(null,null),"other launchers retain standard notification path");
  check(LauncherBadgeRules.count(17,true)==17,"desktop receives aggregate actual unread count");
  check(LauncherBadgeRules.count(0,true)==0,"reading last unread requests removal");
  check(LauncherBadgeRules.count(17,false)==0,"disabled system notification does not request a visible count");
  check(LauncherBadgeRules.count(-1,true)==0&&LauncherBadgeRules.count(123,true)==123,"negative count rejected; launcher handles 99+ display");
  System.out.println("PASS "+n+" launcher badge rules; no Android broadcast or launcher execution.");
 }
}
