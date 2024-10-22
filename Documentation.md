
# Documentation

## Sync Mechanisms

### From quick tile

- Follow your OS-specific directions to add a quick tile
- Select the quick tile labeled `GitSync`
- Clicking the quick tile will trigger a sync (in the same way as the in-app sync button)

### When app opened or closed

![Application observer panel](https://github.com/user-attachments/assets/141a1264-1c78-4d98-82a4-db2fa885ab01)
- Enable the auto sync feature
    - This will prompt you to enable the `Git Sync Accessibility Service`
    - This service is used to watch for the selected app being opened and closed (ignoring keyboards)
    - No data is collected or stored
- Select an application to watch
- Turn on/off sync on application opened or closed


### From custom intent (advanced)

- You can trigger a sync using a custom intent (e.g. from Tasker or other automation apps)
- Intent details
    - Target: `Service`
    - Package: `com.viscouspot.gitsync`
    - Class: `com.viscouspot.gitsync.GitSyncService`
    - Action: `INTENT_SYNC`

## Settings

![Settings button](https://github.com/user-attachments/assets/ea1e416a-03f5-44c7-9aa2-e33d724e622d)

![Settings dialog](https://github.com/user-attachments/assets/225bc25c-33ed-4f4a-9d74-8dc9d2fa2b0e)

### Sync Message
- Customise the commit message template that is used on sync
- Use `%s` to have the date and time inserted in the format `yyyy-MM-dd HH:mm:ss` (`1997-01-01 12:00:00`)

### Gitignore
- Allows editing of the repository's gitignore file
- A list of files and folder paths that should not be synced
- [See here for more](https://git-scm.com/docs/gitignore)

## Extra Settings

![Turn on/off notifications button](https://github.com/user-attachments/assets/f88c8db1-4ca7-4ded-a53d-acc37268725c)
- Turn on/off flashing a small popup message on screen when a sync happens
