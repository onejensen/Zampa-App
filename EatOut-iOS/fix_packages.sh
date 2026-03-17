#!/bin/bash
# Script to fix Firebase package dependencies in Xcode

echo "🔧 Fixing Firebase Package Dependencies..."
echo ""

# Navigate to project directory
cd "$(dirname "$0")"

echo "1. Cleaning DerivedData..."
rm -rf ~/Library/Developer/Xcode/DerivedData/EatOut-*

echo "2. Resolving packages..."
xcodebuild -resolvePackageDependencies -project EatOut.xcodeproj -scheme EatOut

echo ""
echo "✅ Package resolution complete!"
echo ""
echo "📋 Next steps in Xcode:"
echo "   1. Close Xcode completely"
echo "   2. Reopen the project"
echo "   3. Go to File → Packages → Reset Package Caches"
echo "   4. Go to File → Packages → Resolve Package Versions"
echo "   5. Wait for packages to resolve (may take 2-3 minutes)"
echo "   6. Product → Clean Build Folder (Shift+Cmd+K)"
echo "   7. Product → Build (Cmd+B)"
echo ""
echo "The packages will be built automatically when you build the project."




