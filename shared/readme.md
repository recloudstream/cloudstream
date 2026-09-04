## Motivation

This readme is documentation for humans to understand ***why***, and ***not how*** the core architecture
for CloudStream will be designed going forward. 

## Design decisions

The following design decisions has been tried in the sister project QuickNovel with great success, and a 
seamless porting process. For inspiration and code in a similar codebase, take a look at QuickNovel.  

### File structure

The `shared` directory is supposed to be for shared compose UI between platforms, and can act as a glue for business logic. 
This is separate from the `library` that includes the primitives for a headless, cross-platform plugin system.
Note that we do not want a shared screen layout, because each platform has its own design language and quirks.
Instead, the ViewModels should be entirely detached from the rendering process, and each UI component should be shared 
between every platform. This ensures ViewModels cant cheat with dirty escape hatches, a leaky api or 
with UI elements that hijack business logic. 

**Include**:
*Viewmodels*, *UI components* and *Theming*

**Avoid**:
*Entire Screens*, *Plugin APIs* and *Android Layout XML*

Please also note that while it would be nice with an e.g. cross-platform downloader or video player.
It is simply infeasible and undesirable to write such a system. Therefore, each platform should have
a seperate implemenation of platform specific behavior that closly interop with native systems.

### MVI

MVI (Model View Intent) is the core structure for building cross-platform UI we will use.
The reason for this decision is that current MVVM architecture created a lot of boilerplate,
and is very unwieldy when working with compose. We will instead use a single immutable UI state, using
kotlin immutable collections, as that creates thread-safe and performant UI. Additionally, because the viewmodel
is shared between platforms, the Viewmodel must be provided with an implementation specific 
database/preference/downloaded by using dependency injection and composition. 

**Use**: 
*PersistentList*, *val*, *dependency injection* and *composition*

**Avoid**: 
*List*, *var*, *globals* and *inheritance*

Please note that this process aims to replace the current UI in a backwards compatible way. Only
the navigation graph should change to point to the new fragments. Then we can roll back if some 
critical issue is found with the compose rewrite, as well as compare functionality without a checkout. 

### Resources and assets

**Images**: For this project the accepted image format is `XML Drawable resources` instead of 
`ImageVector` or `.svg`. This is primarily due to make the review, cross-platform, and edit 
processing easier. ImageVector is undesirable because file must be reviewed to not contain malicious 
code, and does not have a built-in preview. SVG files on the other hand are currently not natively 
supported on Android. 

**Translation**: CloudStream already has a huge translation, however to make the porting process easier,
we should limit any copy-paste from the android project until everything is converted to compose.
This is in part because weblate is not set up target the cross-platform compose yet. 

**Styling/Themes**: In compose there is no `styles.xml`/`colors.xml`/`dimens.xml`. Therefore,
styling is done entierly in code, but should use the exact same colors to avoid a styling mismatch 
when porting.

**UI/UX**: The intended experience when using CloudStream should be the same, but the UI should be
refreshed to a more Material 3 style, such as rounded corners and a greater focus on light theme 
support. However, no glassmorphic, blurred or gradient UI is accepted.  

### Settings and Mihon

The settings system has been entirely forked from Tachiyomi, now Mihon project. This is because
Mihon has one of the best FOSS codebases for Android, and has a great declarative preference system 
unmatched by any other tested Compose settings system. Unfortunately, this is not provided as a 
library and is therefore instead forked by importing the exact files used. 

### Why compose?

During the lifetime of CloudStream, the app has been redesigned and rewritten several times. 

**C# Downloader → Xamarin Native → Xamarin Cross platform → Android Native XML → ViewBinding → Compose Multiplatform**

We have experimented with different UI technologies such as **Blazor**, **Electron**, **PWA**, 
**WPF**, **UWP**, **Windows Forms**, and **CLI/Console** for CloudStream. 
We have also investigated projectes written in **Flutter** and **React Native**. 

However, none of these frameworks provided a good developer experience, that was also performant and
cross-platform. Compose was the only real option, but did not exist until after we started on 
CloudStream 3. Android Native XML was simply the least bad option when building an Android app.

### CloudStream 4?

The current android app is named CloudStream 3, and this Compose rewrite will be named 
CloudStream 4. However, the android app will be upgraded, not replaced with Compose. It aims to be a 
seamless and gradual rollout of updates for everyone using the Android app. Therefore, CloudStream 4 
is used to refer to the new compose UI, features, and cross-platform support.

## AI Usage

Due to the prevalence and popularity of LLMs, CloudStream 3 has been rewritten entirely 4+ times by 
independent actors using Compose to target desktop with different LLM models. These have not been merged, due to the 
absolute mess of the AI generated codebase, both in code quality and performance. 

Therefore, I (LagradOst) will not accept or allow any pull requests concering the Compose rewrite
before I put the core structures are in place. The compose rewrite is supposed to be a refactor and 
quality improvement for everyone involved, to solve the massive tech debt we already have. XML and 
old plans forced bad design decissions, which we can improve greatly upon. However, in no part do I 
want AI to lay the groundwork for something this important.

The idea is to provide a solid staring point so it is easy for first time contributors and LLMs to
understand and work with the code, not ship a product as fast as possible. This is not a project 
with only one person involved. A messy AI generated codebase makes reviews and contributions very hard 
for everyone involved. 
