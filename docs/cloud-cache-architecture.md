# Cloud cache architecture

OneDrive originals live exclusively in private application storage:

```
filesDir/cyrus_gallery/
  cache/photos/  cache/videos/  cache/thumbnails/
  offline/photos/ offline/videos/
```

`CloudMediaCacheEntity` is unique on `(driveId, oneDriveItemId)` and tracks the remote eTag/cTag and cache state. Local phone media never belongs to this cache.

The future sync manager will download to a temporary file, validate it, atomically move it to the managed directory, then mark the Room record complete. Cache clearing and LRU eviction can remove only `CACHED` app-private files. They may never call Microsoft Graph deletion APIs, affect pinned files, or operate on device media.

Graph synchronization will use pagination and incremental tokens, comparing eTag/cTag before transfers. WorkManager constraints will be selected from `CloudCachePolicy`, avoiding repeat downloads and allowing thumbnails/metadata to remain usable offline.
