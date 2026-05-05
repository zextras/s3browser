package com.zextras.s3browser.domain;

import java.util.List;

public record BrowseResult(
    String prefix,
    String parentPrefix,
    List<FolderItem> folders,
    List<ObjectItem> objects
) {
}

