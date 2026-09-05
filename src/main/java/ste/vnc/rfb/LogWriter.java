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

import java.util.logging.Level;
import java.util.logging.Logger;

public final class LogWriter {

    private final Logger logger;

    public LogWriter(final String name) {
        this.logger = Logger.getLogger(name);
    }

    public LogWriter(final Class<?> clazz) {
        this(clazz.getName());
    }

    public void info(final String message) {
        logger.info(message);
    }

    public void warning(final String message) {
        logger.log(Level.WARNING, message);
    }

    public void error(final String message) {
        logger.log(Level.SEVERE, message);
    }

    public void error(final String message, final Throwable t) {
        logger.log(Level.SEVERE, message, t);
    }

    public void finest(final String message) {
        logger.finest(message);
    }

    public boolean isFineLoggable() {
        return logger.isLoggable(Level.FINE);
    }

    public boolean isFinerLoggable() {
        return logger.isLoggable(Level.FINER);
    }

    public boolean isFinestLoggable() {
        return logger.isLoggable(Level.FINEST);
    }
}
