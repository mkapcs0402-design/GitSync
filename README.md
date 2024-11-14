<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="140" />

  <h3>GitSync</h3>
  <h4>Android git client for syncing a repository between remote and a local directory</h4>
  
  <p align="center">
    <img src="https://img.shields.io/github/license/ViscousPotential/GitSync?v=1">
    <img src="https://img.shields.io/github/last-commit/ViscousPotential/GitSync?v=1">
    <img src="https://img.shields.io/github/stars/ViscousPotential/GitSync?v=1" alt="stars">
    <img src="https://img.shields.io/github/downloads/ViscousPotential/GitSync/total?v=1" alt="downloads">
  </p>

  <a href="https://buymeacoffee.com/viscouspotential" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>


  <p align="center">
    <a href="https://github.com/ViscousPotential/GitSync/blob/master/Documentation.md">Documentation</a>
  </p>

</div>


GitSync is an Android git client that aims to simplify the process of syncing a folder between a git remote and a local directory. Works in the background to keep your files synced with a simple one-time setup and numerous options for activating manual syncs

- Authenticate with Github.com
- Clone a remote repository
- Sync repository
    - Fetch changes
    - Pull changes
    - Commit new changes
    - Push changes
    - **Resolve merge conflicts**
- Sync mechanisms
    - From a quick tile
    - When an app is opened or closed
    - From a custom intent (advanced)
- Settings
    - Customise sync message
    - Edit .gitignore file
  
Give us a ⭐ if you like our work. Much appreciated!

## Variants

There are two variants of GitSync available for the most recent releases.

We recommend using the "All files variant" for the best experience, as it will allow you to use a larger range of folders with the app.

- Base variant 
  - `app-all-files-variant-release-signed.apk`
- All files variant
  - `app-base-release-signed.apk`

The main difference between these versions is the use of "All files access" permission.

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

Consider sponsoring! Any help is hugely appreciated!


## Authors

- [@ViscousPotential](https://github.com/ViscousPot)


## Acknowledgements

 - [KGit](https://github.com/sya-ri/KGit)


