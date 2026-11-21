/*
 * This file is part of OneConfig.
 * OneConfig - Next Generation Config Library for Minecraft: Java Edition
 * Copyright (C) 2021~2024 Polyfrost.
 *   <https://polyfrost.org> <https://github.com/Polyfrost/>
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *   OneConfig is licensed under the terms of version 3 of the GNU Lesser
 * General Public License as published by the Free Software Foundation, AND
 * under the Additional Terms Applicable to OneConfig, as published by Polyfrost,
 * either version 1.0 of the Additional Terms, or (at your option) any later
 * version.
 *
 *   This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 * License.  If not, see <https://www.gnu.org/licenses/>. You should
 * have also received a copy of the Additional Terms Applicable
 * to OneConfig, as published by Polyfrost. If not, see
 * <https://polyfrost.org/legal/oneconfig/additional-terms>
 */

package org.polyfrost.oneconfig.api.ui.v1.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File selectors through OneClient's launcher when the game runs in its sandbox
 * <p>
 * The sandbox only lets the game see its own instance, so a native dialog opened by the game can't show the
 * user's files. OneClient's agent puts {@code org.polyfrost.oneclient.agent.api.OneClientSandbox} on the boot
 * class path; its picker runs in the launcher and hands back a path the game can use (a copy inside the
 * instance for a picked file). Message boxes and notifications stay with the native implementation
 */
final class OneClientDialogs implements TinyFdApi {
    private static final Logger LOGGER = LogManager.getLogger("OneConfig/Dialogs");
    private static final String API = "org.polyfrost.oneclient.agent.api.OneClientSandbox";

    private final TinyFdApi delegate;
    private final Method pickFile;

    private OneClientDialogs(TinyFdApi delegate, Method pickFile) {
        this.delegate = delegate;
        this.pickFile = pickFile;
    }

    /** {@code delegate} itself unless the game runs in OneClient's sandbox */
    static TinyFdApi wrap(TinyFdApi delegate) {
        if (!Boolean.getBoolean("oneclient.sandboxed")) return delegate;
        try {
            Class<?> api = Class.forName(API, true, null);
            if (!Boolean.TRUE.equals(api.getMethod("isSandboxed").invoke(null))) return delegate;
            return new OneClientDialogs(delegate, api.getMethod("pickFile", String.class, String.class, String[].class));
        } catch (Throwable t) {
            LOGGER.warn("Running in OneClient's sandbox without its picker, file dialogs may not see your files", t);
            return delegate;
        }
    }

    @Nullable
    private Path pick(String kind, @Nullable String title, @Nullable String[] filterPatterns, @Nullable String filterDescription) {
        String[] filters = new String[0];
        if (filterPatterns != null && filterPatterns.length > 0) {
            // OneClient's filters are "description|ext;ext" and take tinyfd's "*.png" patterns as they are
            String patterns = String.join(";", filterPatterns);
            filters = new String[]{filterDescription == null || filterDescription.isEmpty() ? patterns : filterDescription + "|" + patterns};
        }
        try {
            String picked = (String) pickFile.invoke(null, kind, title == null ? "" : title, filters);
            return picked == null ? null : Paths.get(picked);
        } catch (Throwable t) {
            LOGGER.error("OneClient's file picker failed", t);
            return null;
        }
    }

    @Override
    public Path openSaveSelector(@Nullable String title, @Nullable String defaultFilePath, String[] filterPatterns, @Nullable String filterDescription) {
        return pick("save", title, filterPatterns, filterDescription);
    }

    @Nullable
    @Override
    public Path openFileSelector(@Nullable String title, @Nullable String defaultFilePath, String[] filterPatterns, @Nullable String filterDescription) {
        return pick("open", title, filterPatterns, filterDescription);
    }

    @Override
    public Path[] openMultiFileSelector(@Nullable String title, @Nullable String defaultFilePath, String[] filterPatterns, @Nullable String filterDescription) {
        // the launcher's picker hands back one file
        Path picked = pick("open", title, filterPatterns, filterDescription);
        return picked == null ? new Path[0] : new Path[]{picked};
    }

    @Override
    public Path openFolderSelector(@Nullable String title, @Nullable String defaultFolderPath) {
        return pick("folder", title, null, null);
    }

    @Override
    public boolean showMessageBox(String title, String message, @NotNull String dialog, String icon, boolean defaultValue) {
        return delegate.showMessageBox(title, message, dialog, icon, defaultValue);
    }

    @Override
    public int showNotification(String title, String message, String icon) {
        return delegate.showNotification(title, message, icon);
    }
}
