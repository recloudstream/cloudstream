/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.car.app.model;

import static java.util.Objects.requireNonNull;

import androidx.annotation.FloatRange;
import androidx.car.app.annotations.CarProtocol;
import androidx.car.app.annotations.ExperimentalCarApi;
import androidx.car.app.annotations.KeepFields;
import androidx.car.app.annotations.RequiresCarApi;
import androidx.car.app.model.constraints.CarColorConstraints;
import androidx.core.math.MathUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A model that represents a progress bar to display in the car screen.
 *
 * <p>A progress bar displays the progress of a task or playback. It is a non-interactive element
 * that can be used in templates that support it, such as in a {@link Row} or {@link GridItem}.

 * <p>The progress bar takes in the progress value as a mandatory field and can optionally set a
 * custom color.
 */
@CarProtocol
@KeepFields
@RequiresCarApi(9)
@ExperimentalCarApi
public final class CarProgressBar {
    private final float mProgress;
    private final @Nullable CarColor mColor;

    /** Returns the progress between 0.0 and 1.0. */
    public float getProgress() {
        return mProgress;
    }

    /** Returns the color of the progress bar, or {@code null} if not set. */
    public @Nullable CarColor getColor() {
        return mColor;
    }

    @Override
    public @NonNull String toString() {
        return "[progress: " + mProgress + ", color: " + mColor + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mProgress, mColor);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CarProgressBar)) {
            return false;
        }
        CarProgressBar otherBar = (CarProgressBar) other;
        return mProgress == otherBar.mProgress && Objects.equals(mColor, otherBar.mColor);
    }

    CarProgressBar(Builder builder) {
        mProgress = builder.mProgress;
        mColor = builder.mColor;
    }

    /** Constructs an empty instance, used by serialization code. */
    private CarProgressBar() {
        mProgress = 0;
        mColor = null;
    }

    /** A builder of {@link CarProgressBar}. */
    public static final class Builder {
        float mProgress;
        @Nullable CarColor mColor;

        /**
         * Sets the color of the progress bar.
         *
         * <p>If a color is not set, or if the provided color does not pass a contrast check, the
         * host will use a default color.
         *
         * @throws NullPointerException if {@code color} is {@code null}
         */
        public @NonNull Builder setColor(@NonNull CarColor color) {
            CarColorConstraints.UNCONSTRAINED.validateOrThrow(requireNonNull(color));
            mColor = color;
            return this;
        }

        /** Constructs the {@link CarProgressBar} defined by this builder. */
        public @NonNull CarProgressBar build() {
            return new CarProgressBar(this);
        }

        /**
         * Creates a builder with the specified progress.
         *
         * <p>Values outside the range [0.0, 1.0] will be coerced to the nearest bound.
         *
         * @param progress the progress, from 0.0 to 1.0
         * @throws IllegalArgumentException if {@code progress} is {@code NaN} or infinite
         */
        public Builder(@FloatRange(from = 0.0f, to = 1.0f) float progress) {
            if (Float.isNaN(progress) || Float.isInfinite(progress)) {
                throw new IllegalArgumentException("Progress cannot be NaN or infinite");
            }
            mProgress = MathUtils.clamp(progress, 0.0f, 1.0f);
        }
    }
}
