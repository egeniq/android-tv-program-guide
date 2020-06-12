# Changelog


### 2019-02-10

* Fixed a bug where focus would be lost sometimes when switching between days.

### 2019-12-20

* Fixed a crash in the demo, which was because of a missing `AndroidThreeTen.init()`.
* Fixed a crash in the library which happened when restoring the program guide from the view saved states (`ClassNotFoundException: androidx.recyclerview.widget.RecyclerView$SavedState`).

### 2020-06-12

* Added a method to scroll to a specific channel: `ProgramGuideFragment.scrollToChannelWithId(channelId)`
* Fixed an issue where the EPG fragment would be stuck on loading state when the fragment gets restored.
* Fixed an issue where scrolling to the live position would not put focus on the programme if no actual scroll was done.
