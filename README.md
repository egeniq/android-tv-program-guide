android-tv-program-guide
========================

![Scrolling demo](https://raw.githubusercontent.com/egeniq/android-tv-program-guide/master/documentation/scrolling.gif)

This is an open-source EPG (electronic program guide) implementation **for Android TV only**.
The code in this repository is based on the [official Android TV channel browser implementation](https://android.googlesource.com/platform/packages/apps/TV/+/android-live-tv/src/com/android/tv/guide/).

We have removed the parts where it fetches all the program data from the cable TV connection, and
replaced it with the ability to set your own data. Also we have converted the code to Kotlin and AndroidX.

![The demo project](https://raw.githubusercontent.com/egeniq/android-tv-program-guide/master/documentation/demo_overview.png)


### Features and usage

The programs you give to the program guide manager will be put in a row. Each channel has its own row,
so you can quickly switch channels (up and down) or scroll in time (left and right). Next to that, there 
are quick buttons to switch between days and times of the day. Also, you can jump to the current time
by clicking on the jump to live button.

![Selecting a day](https://raw.githubusercontent.com/egeniq/android-tv-program-guide/master/documentation/day_selector.png)

By extending the ProgramGuideFragment, you can get callbacks for when the selected program has changed,
a program has been clicked, or the user requested that the data for a different day should be loaded.


### Package

The repo consists of a library and demo project. The demo project showcases the usage and provides a quick way 
to test the workings of the library module.

Since there can be a lot of program guide designs, we **do not provide** the library as a package.
You will probably have to fork this project to modify it according to your specifications and layouts.
For the same reason, a lot of features are not configurable, to keep the code simple. If you feel that
we should still offer a package, feel free to open a ticket to discuss :)


### Support

Have you found a bug, or does the library not work on your device? Open a ticket with the reproduction
steps and the manufacturer and model of your device, and we will do our best to help you.
Pull requests are always welcome.

### License

This project is licensed under [the Apache 2.0 license](https://raw.githubusercontent.com/egeniq/android-tv-program-guide/master/LICENSE)
