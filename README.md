# wallpaperer
A gallery and homescreen wallpaper changer app for Android.

<img src="/images/Screenshot_20221018_134243.png" alt="wallpaperer app running on a Nexus 6 Pro" width="400"/>

## Summary
The Wallpaperer workflow is simple:
1. Add images using the (+) button, or share images from other apps to Wallpaperer
2. Toggle the changer control to the *on* position
3. Set your desired wallpaper changing delay/interval in the app settings (default: 15 minutes)

## Features
* ![add](/images/add.png) Touch this button to add a new image to the library. You can choose an image that is stored locally on your device. All images added to Wallpaperer are copied to the app and will remain even if the original source is deleted. A white ring around the button indicates progress toward the next scheduled wallpaper change.
* ![toggle](/images/toggle.png) When enabled, the homescreen wallpaper will be changed to a random image on a customizable time interval (see: Settings)
* ![cycle](/images/cycle.png) Touch this control to immediately change the homescreen wallpaper to a new random image (from this app's gallery)
* ![next](/images/next.png) This button appears on every image tile. Touch it to immediately change the homescreen wallpaper to the respective image.
* ![share](/images/share.png) This button appears on every image tile. Touch it to share the respective image with another app. NOTE: The image that is sent may have been recompressed and be lower quality than the original source (see: Settings).
* Tap a thumbnail to view a fullscreen preview of the image.
* Swipe the thumbnail off the screen to delete it from the gallery. There is a short time where you can press *UNDO* after swiping away an image. NOTE: This will delete the copy of the image maintained by Wallpaperer. It **does not** delete the original source that was added/shared to the gallery.
* Share an image from any app to Wallpaperer. Sharing an image to this app will create a copy of the image in the Wallpaperer gallery.

## Settings

<img src="/images/Screenshot_20221018_134307.png" alt="wallpaperer app settings screen" width="400"/>

### Thumbnail columns
Set the maximum number of image thumbnails you want to see on each row in the gallery. Depending on screen size and dpi, the app may display fewer thumbnails per row than is specified in this preference. This is simply to leave enough room in the thumbnail for the action buttons.

### Show wallpaper metadata
Some info about the image, including size, filename, and image type is displayed in the thumbnail.

### Crop image to fill screen
When enabled, the image will be stretched (maintaining image aspect ratio) to eliminate any black bars (letterboxing), cropping out portions of the image as necessary. Disable if you want the entire image visible on the homescreen (with black bars to fill in the empty space).

### Sleep only
Some versions of Android have a *feature* that causes apps to restart when the homescreen wallpaper is changed. This option, if enabled, will only allow the wallpaper to be changed during sleep to avoid interruption to your workflow.

### Recompress images
Walpaperer makes a copy of every image added to its gallery. This option will attempt to compress those image copies and reduce the amount of storage space they consume. This is lossy compression that may result in lower visual quality.

### Wallpaper delay
The interval between automatic homescreen wallpaper changes. The minimum delay (Android work request limitation) is 15 minutes. You may specify between 15 minutes and 24 hours (default: 15 minutes).

### Battery optimization settings
Android aggresively manages the power consumption of apps by placing them into a *Doze* mode after the device has been inactive for a while. While in doze mode, the wallpaper changing job will not run and its next run time becomes unpredictable. To make the wallpaper interval more predictable it is recommended that you don't allow Android to *optimize* this app. Touch this setting to open the Android system menu for battery optimization. Use that system menu to remove this app from the optimized apps list.

## Build
Clone and build this project with [Android Studio](https://developer.android.com/studio).

## License

[MIT License](/LICENSE.MD) Copyright © 2022 Chad Barnes

Software included in this project include:

* medyo/android-about-page © 2016 Mehdi Sakout The MIT License (MIT)
* bumptech/glide BSD, part MIT and Apache 2.0 licenses
* zhanghai/AndroidFastScroll © 2019 Google LLC Apache 2.0 License
* amlcurran/ShowcaseView © 2012-2014 Alex Curran Apache 2.0 License
* stfalcon-studio/StfalconImageViewer © 2018 stfalcon.com Apache 2.0 License
