[app]
title = DsCome Host
package.name = dscome_host
package.domain = com.dscome
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,json
version = 1.0.0

requirements = python3,kivy==2.3.0,colorama

orientation = portrait
fullscreen = 0

android.permissions = READ_EXTERNAL_STORAGE,WRITE_EXTERNAL_STORAGE,INTERNET
android.api = 33
android.minapi = 24
android.ndk = 25b
android.archs = arm64-v8a, armeabi-v7a

android.allow_backup = True

icon.filename = %(source.dir)s/assets/icon.png

[buildozer]
log_level = 2
warn_on_root = 1
