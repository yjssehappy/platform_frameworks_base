/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.classifier;

import android.view.MotionEvent;

import java.lang.Math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A classifier which calculates the variance of differences between successive angles in a stroke.
 * For each stroke it keeps its last three points. If some successive points are the same, it
 * ignores the repetitions. If a new point is added, the classifier calculates the angle between
 * the last three points. After that, it calculates the difference between this angle and the
 * previously calculated angle. Then it calculates the variance of the differences from a stroke.
 * To the differences there is artificially added value 0.0 and the difference between the first
 * angle and PI (angles are in radians). It helps with strokes which have few points and punishes
 * more strokes which are not smooth. This classifier also tries to split the stroke into two parts
 * int the place in which the biggest angle is. It calculates the angle variance of the two parts
 * and sums them up. The reason the classifier is doing this, is because some human swipes at the
 * beginning go for a moment in one direction and then they rapidly change direction for the rest
 * of the stroke (like a tick). The final result is the minimum of angle variance of the whole
 * stroke and the sum of angle variances of the two parts split up.
 */
public class AnglesVarianceClassifier extends StrokeClassifier {
    private HashMap<Stroke, Data> mStrokeMap = new HashMap<>();

    public AnglesVarianceClassifier(ClassifierData classifierData) {
        mClassifierData = classifierData;
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            mStrokeMap.clear();
        }

        for (int i = 0; i < event.getPointerCount(); i++) {
            Stroke stroke = mClassifierData.getStroke(event.getPointerId(i));

            if (mStrokeMap.get(stroke) == null) {
                mStrokeMap.put(stroke, new Data());
            }
            mStrokeMap.get(stroke).addPoint(stroke.getPoints().get(stroke.getPoints().size() - 1));
        }
    }

    @Override
    public float getFalseTouchEvaluation(int type, Stroke stroke) {
        return AnglesVarianceEvaluator.evaluate(mStrokeMap.get(stroke).getAnglesVariance());
    }

    private static class Data {
        private List<Point> mLastThreePoints = new ArrayList<>();
        private float mFirstAngleVariance;
        private float mPreviousAngle;
        private float mBiggestAngle;
        private float mSumSquares;
        private float mSecondSumSquares;
        private float mSum;
        private float mSecondSum;
        private float mCount;
        private float mSecondCount;

        public Data() {
            mFirstAngleVariance = 0.0f;
            mPreviousAngle = (float) Math.PI;
            mBiggestAngle = 0.0f;
            mSumSquares = mSecondSumSquares = 0.0f;
            mSum = mSecondSum = 0.0f;
            mCount = mSecondCount = 1.0f;
        }

        public void addPoint(Point point) {
            // Checking if the added point is different than the previously added point
            // Repetitions are being ignored so that proper angles are calculated.
            if (mLastThreePoints.isEmpty()
                    || !mLastThreePoints.get(mLastThreePoints.size() - 1).equals(point)) {
                mLastThreePoints.add(point);
                if (mLastThreePoints.size() == 4) {
                    mLastThreePoints.remove(0);

                    float angle = mLastThreePoints.get(1).getAngle(mLastThreePoints.get(0),
                            mLastThreePoints.get(2));

                    float difference = angle - mPreviousAngle;

                    // If this is the biggest angle of the stroke so then we save the value of
                    // the angle variance so far and start to count the values for the angle
                    // variance of the second part.
                    if (mBiggestAngle < angle) {
                        mBiggestAngle = angle;
                        mFirstAngleVariance = getAnglesVariance(mSumSquares, mSum, mCount);
                        mSecondSumSquares = 0.0f;
                        mSecondSum = 0.0f;
                        mSecondCount = 1.0f;
                    } else {
                        mSecondSum += difference;
                        mSecondSumSquares += difference * difference;
                        mSecondCount += 1.0;
                    }

                    mSum += difference;
                    mSumSquares += difference * difference;
                    mCount += 1.0;
                    mPreviousAngle = angle;
                }
            }
        }

        public float getAnglesVariance(float sumSquares, float sum, float count) {
            return sumSquares / count - (sum / count) * (sum / count);
        }

        public float getAnglesVariance() {
            return Math.min(getAnglesVariance(mSumSquares, mSum, mCount),
                    mFirstAngleVariance + getAnglesVariance(mSecondSumSquares, mSecondSum,
                            mSecondCount));
        }
    }
}