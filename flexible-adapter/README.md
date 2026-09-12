# flexible-adapter (vendored)

This module is the base `flexible-adapter` library from
[arkon/FlexibleAdapter](https://github.com/arkon/FlexibleAdapter) pinned to commit
`c80135339bcff5f7f8c2c2380329dfc155b26232` (the same revision J2K and this app's
download UI were built against), copied verbatim under its original Apache-2.0 license.

It used to be fetched from JitPack as `com.github.arkon.FlexibleAdapter:flexible-adapter`,
but JitPack can no longer build that revision: the fork's `eu.davidea:grabver` plugin
depends on `nu.studer:java-ordered-properties:1.0.1`, which was deleted from Maven
Central, so any fresh checkout would fail with an unresolvable dependency.

Do not modify these sources unless you also intend to move the whole app off the
FlexibleAdapter API.
