# Changelog

### 2020-10-30

* __NEW:__ Added a method to update a program in the grid. `ProgramGuideFragment.updateProgram(program)` can be used to update a pre-existing program with the same ID. You can update the title, the clickability and the underlying program data object. The demo was also updated with an example, it will append some text to the title if you click on a program.
* __NEW:__ Programs in the past will now have a slightly darker background and text color. You can override these colors to change them, just search for `in_past` in `colors.xml`
* __NEW:__ You can now supply your own error message in State.Error, also added a style for the TextView for better customization.

### 2020-09-29

* Currently selected channel has a highlighted background now.

### 2020-09-07

* Fixed issues with display on RTL devices.

### 2020-06-12

* __NEW:__ Added a method to scroll to a specific channel: `ProgramGuideFragment.scrollToChannelWithId(channelId)`
* Fixed an issue where the EPG fragment would be stuck on loading state when the fragment gets restored.
* Fixed an issue where scrolling to the live position would not put focus on the programme if no actual scroll was done.

### 2019-12-20

* Fixed a crash in the demo, which was because of a missing `AndroidThreeTen.init()`.
* Fixed a crash in the library which happened when restoring the program guide from the view saved states (`ClassNotFoundException: androidx.recyclerview.widget.RecyclerView$SavedState`).

### 2019-02-10

* Fixed a bug where focus would be lost sometimes when switching between days.