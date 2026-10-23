# Add project specific ProGuard rules here.
# Accessibility service + node engine rely on reflection-free plain code, so the
# default rules are sufficient. Keep the service class name intact.
-keep class hello.litiaotiao.app.service.** { *; }
