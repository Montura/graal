mkdir xcode
# iOS
xcodebuild -sdk iphoneos -arch arm64 -project graal-svm-ios.xcodeproj -scheme graal-svm-arm64-debug > xcode/graal-svm-arm64-debug.log
xcodebuild -sdk iphoneos -arch arm64 -project graal-svm-ios.xcodeproj -scheme graal-svm-arm64-release > xcode/graal-svm-arm64-release.log

# iOS Simulator
xcodebuild -sdk iphonesimulator -arch x86_64 -project graal-svm-ios.xcodeproj -scheme graal-svm-x86-64-debug > xcode/graal-svm-x86-64-debug.log
xcodebuild -sdk iphonesimulator -arch x86_64 -project graal-svm-ios.xcodeproj -scheme graal-svm-x86-64-release > xcode/graal-svm-x86-64-release.log

# Mac Catalyst (experimental)
#xcodebuild -sdk macosx -arch x86_64 -project graal-svm-ios.xcodeproj -scheme graal-svm-x86-64-debug
#xcodebuild -sdk macosx -arch x86_64 -project graal-svm-ios.xcodeproj -scheme graal-svm-x86-64-release
#xcodebuild -sdk macosx -arch arm64 -project graal-svm-ios.xcodeproj -scheme graal-svm-arm64-ios-debug
#xcodebuild -sdk macosx -arch arm64 -project graal-svm-ios.xcodeproj -scheme graal-svm-arm64-ios-release

ls -la xcode

lipo -info xcode/graal-svm-arm64-ios-d.a
lipo -info xcode/graal-svm-arm64-ios-r.a
lipo -info xcode/graal-svm-x86-64-ios-simulator-d.a
lipo -info xcode/graal-svm-x86-64-ios-simulator-r.a
#lipo -info xcode/graal-svm-x86-64-mac-catalyst-d.a
#lipo -info xcode/graal-svm-x86-64-mac-catalyst-r.a
#lipo -info xcode/graal-svm-arm64-mac-catalyst-d.a
#lipo -info xcode/graal-svm-arm64-mac-catalyst-r.a