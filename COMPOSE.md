# Migration guide to Compose

### 1. MVI instead of MVVM 

The current design of CloudStream loosely uses the MVVM architecture.

This means that the UI invokes the viewmodel with function calls, and it responds with LiveData fields that are observed. While this has worked, it generates a lot of boilerplate and has created some friction.

To make it easier to work with Compose, the new architecture will be based on MVI. In short this means that the viewmodel exposes a singular immutable class that is observed, and receives all UI events with a singular event that is a sealed class. All the UI should be able to be recreated based on this singular state class, and all interactions should be able to be replayed using only the event callback.

For a more detailed overview, see: https://www.youtube.com/watch?v=b2z1jvD4VMQ

This is part of the effort to make CloudStream cross platform, as it allows us to decouple UI and logic.

### 2. KMP-compatible libraries

We plan to leverage Kotlin's KMP project to compile our code to different architectures. However, this requires us to only use KMP-compatible libraries, no Java. Therefore any pull requests must ensure that they use KMP-compatible libraries only.

### 3. UI Changes

While migrating to the new compose UI, you also have the opportunity to change the UI. However, this should only be to freshen up the UI, not completely redesign it. It is also important to stress that this process should not lose any features of the old UI, and be very conservative with adding new features.