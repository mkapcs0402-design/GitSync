<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="140" />

  <h3>GitSync</h3>
  <h4>Android git client for syncing a repository between remote and a local directory</h4>
  
  <p align="center">
    <img src="https://img.shields.io/github/license/ViscousPot/GitSync" alt="license">
    <img src="https://img.shields.io/github/last-commit/ViscousPot/GitSync?v=1" alt="last commit">
    <img src="https://img.shields.io/github/stars/ViscousPot/GitSync?v=1" alt="stars">
    <img src="https://img.shields.io/github/downloads/ViscousPot/GitSync/total" alt="downloads">
    <a href="https://github.com/sponsors/ViscousPot"><img src="https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86" alt="sponsor"></a>
  </p>

  <p align="center">
    <a href="https://github.com/ViscousPot/GitSync/blob/master/Documentation.md">Documentation</a>
  </p>
  <br />
  <a href="https://play.google.com/store/apps/details?id=com.viscouspot.gitsync" target="_blank"><img src="https://github.com/user-attachments/assets/168cb841-392d-493a-bc47-a9e3e8a61a62" alt="Get it on Google Play" style="width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;" ></a>
  
  <br />
  <br />

</div>


GitSync is an Android git client that aims to simplify the process of syncing a folder between a git remote and a local directory. It works in the background to keep your files synced with a simple one-time setup and numerous options for activating manual syncs

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

## Build Instructions

If you just want to try the app out, feel free to use a pre-built release from the [Releases](https://github.com/ViscousPotential/GitSync/releases) page

### 1. Setup 
- Clone the project
```bash
  git clone https://github.com/ViscousPot/GitSync.git
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
  - Homepage URL: `https://github.com/ViscousPot/GitSync`
  - Authorization callback URL: `gitsync://auth`
  - Enable Device Flow: `leave unchecked` 
- Fill `Secrets.kt` with the new OAuth App ID and SECRET

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

Consider [sponsoring](https://github.com/sponsors/ViscousPot)! Any help is hugely appreciated!


## Authors

- [@ViscousPot](https://github.com/ViscousPot)


## Acknowledgements

 - [KGit](https://github.com/sya-ri/KGit)


