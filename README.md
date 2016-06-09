# Android-QuartzSecurity
An Android app demonstrating how to use Quartz runtime security services.

There is an accompanying MS Word document that explains everything. It will be converted to MD/HTML once the project is ready.

## Follow up issues with Android team
1. If we use `new Portal(mPortalURL, true);` to login, how do we logout?
2. If using the load a portal constructor as a user login facility and then `loadStatus = arcgisPortal.getLoadStatus(); if (loadStatus != LoadStatus.LOADED)` how do we determine the cause of the login failure?
3. How do we indicated we want to use oAuth? Right now it uses Token (as far as I can tell.)

