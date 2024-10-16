# <img alt="alt text" src="app/src/main/res/drawable-mdpi/gitsync_notif.png" /> GitSync

<a href="https://buymeacoffee.com/viscouspotential" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>

Android mobile git client for syncing a repository between remote and a local directory

![Screenshot](https://i.postimg.cc/qR59qYfm/Screenshot-20240828-183827-Git-Sync.png)
## Features

- Authenticate with Github.com
- Clone remote repository
- Sync repository
    - Fetch changes
    - Pull changes
    - Commit new changes
    - Push changes
- Sync mechanisms
    - From quick tile
    - When app opened or closed
    - From custom intent (advanced)

## Variants

There are two variants of GitSync available for the most recent releases.
- Base variant 
  - `app-all-files-variant-release-signed.apk`
- All files variant
  - `app-base-release-signed.apk`

The main difference between these versions is the use of "All files access" permission.

We recommend using the "All files variant" for the best experience, as it will allow you to use a larger range of folders with the app.

## [Documentation](Documentation.md)

## Build Instructions

If you just want to try the app out, feel free to use a pre-built release from the [Releases](https://github.com/ViscousPotential/GitSync/releases) page

### 1. Setup 
- Clone the project
```bash
  git clone https://github.com/ViscousPotential/GitSync.git
```


- Go to the project directory

```bash
  cd GitSync
```

- Open the project in Android Studio
- Sync the gradle project

### 2. Secrets 
- Rename `Secrets.kt.template` to `Secrets.kt`
- Visit `https://github.com/settings/developers`
- Select `OAuth Apps`
- Select `New OAuth App`
  - Application Name: GitSync
  - Homepage URL: `https://github.com/ViscousPotential/GitSync`
  - Authorization callback URL: `gitsync://auth`
  - Enable Device Flow: `leave unchecked` 
- Fill `Secrets.kt` with the new OAuth app ID and SECRET

### 3. Build & Run
- There are two flavors of GitSync
  - The base variant
    - Includes all the base functionality of GitSync, but doesn't have access to some files and folders on the device
    - Equivalent to the version available on the PlayStore
  - The all-files variant
    - Includes all the functionality of the base variant + an extra permission to give the app full access to the device files and folders
    - This is useful if you already have a repository cloned on your device
    - GitSync does not access any files outside of the directory selected in the app nor is anything stored or sent from the selected directory
- Select a build variant (base is the default)
- Build from within Android Studio

## Support

For support, email bugs.viscouspotential@gmail.com.


## Authors

- [@ViscousPotential](https://github.com/ViscousPotential)


## Acknowledgements

 - [KGit](https://github.com/sya-ri/KGit)

