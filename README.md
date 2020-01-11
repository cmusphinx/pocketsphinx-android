About
=====

This project is a wrapper for Pocketsphinx for Android providing
high-level interface for recognizing the microphone input.

Build
=====

You will need SWIG, Gradle and Android NDK to build a distributable
archive of pocketsphinx for Android. It is better to use recent versions.

You need to checkout sphinxbase, pocketsphinx and pocketsphinx-android
and put them in the same folder.

```
Root folder
 \_pocketsphinx
 \_sphinxbase
 \_pocketsphinx-android
```

Older versions might be incompatible with the latest pocketsphinx-android,
so you need to make sure you are using latest versions. You can use
the following command to checkout from repository:

```
git clone https://github.com/cmusphinx/sphinxbase
git clone https://github.com/cmusphinx/pocketsphinx
git clone https://github.com/cmusphinx/pocketsphinx-android
```

After checkout you need to update the file 'local.properties' in the
project root and define the following properties:

  * sdk.dir - path to Android SDK
  * ndk.dir - path to Android NDK
  * pocketsphinx.dir - path to pocketsphinx folder
  * sphinxbase.dir - path to sphinxbase folder

For example:

```
sdk.dir=/Users/User/Library/Android/sdk
ndk.dir=/Users/User/Library/Android/sdk/ndk-bundle
pocketsphinx.dir=/Users/user/pocketsphinx
sphinxbase.dir=/Users/user/sphinxbase
```

After everything is set, run `./gradlew build`. It will create
pocketsphinx-android-5prealpha-release.aar and
pocketsphinx-android-5prealpha-debug.aar in build/output.

Using the library
=================

Add bintray maven to your repositories

    allprojects {
        repositories {
            maven {
                url  "https://dl.bintray.com"
            }
            jcenter()
            google()
        }
    }


Add `pocketsphinx-android` to your dependencies

    dependencies {
        implementation 'edu.cmu.pocketsphinx.android:pocketsphinx-android:5prealpha@aar'
    }


Using the library locally
=================

Library is distributed as android archive AAR. You can add it to your project
as usual with Android Studio or directly in gradle

    dependencies {
        compile (name:'pocketsphinx-android-debug', ext:'aar')
    }

    repositories {
        flatDir {
            dirs 'libs'
        }
    }

For further information on usage please see the wiki page:

http://cmusphinx.sourceforge.net/wiki/tutorialandroid
