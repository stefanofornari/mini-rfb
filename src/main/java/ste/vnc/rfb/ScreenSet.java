/*
 * Copyright 2026 ste.vnc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ste.vnc.rfb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScreenSet {

    private final List<Screen> screens;

    public ScreenSet() {
        this.screens = new ArrayList<>();
    }

    public void addScreen(final Screen screen) {
        screens.add(screen);
    }

    public void removeScreen(final int id) {
        screens.removeIf(screen -> screen.id == id);
    }

    public int size() {
        return screens.size();
    }

    public List<Screen> screens() {
        return Collections.unmodifiableList(screens);
    }
}
