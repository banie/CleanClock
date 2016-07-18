/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.banuu.cleanclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import java.util.TimeZone;

import static android.graphics.Paint.Style.STROKE;

/**
 * This widget display an analogic clock with two hands for hours and
 * minutes.
 */
public class AnalogClock extends View {
  private final LayerDrawable mHourHand;
  private final LayerDrawable mMinuteHand;
  private final LayerDrawable mSecondHand;
  private int mInternalPadding;
  private int mCenterX;
  private int mCenterY;
  private int mRadius;
  private final Handler mHandler = new Handler();
  private final Context mContext;
  private Time mCalendar;
  private boolean mAttached;
  private float mSeconds;
  private float mMinutes;
  private float mHour;
  private boolean mChanged;
  private String mTimeZoneId;
  private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
        String tz = intent.getStringExtra("time-zone");
        mCalendar = new Time(TimeZone.getTimeZone(tz).getID());
      }
      onTimeChanged();
      invalidate();
    }
  };
  private final Runnable mClockTick = new Runnable() {

    @Override
    public void run() {
      onTimeChanged();
      invalidate();
      AnalogClock.this.postDelayed(mClockTick, 1000);
    }
  };
  private boolean mNoSeconds = false;
  private Paint mCirclePaint;

  public AnalogClock(Context context) {
    this(context, null);
  }

  public AnalogClock(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public AnalogClock(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    mContext = context;
    Resources r = mContext.getResources();
    mHourHand = (LayerDrawable) ContextCompat.getDrawable(context, R.drawable.hourhand);
    mMinuteHand = (LayerDrawable) ContextCompat.getDrawable(context, R.drawable.minhand);
    mSecondHand = (LayerDrawable) ContextCompat.getDrawable(context, R.drawable.secondhand);

    int dialColor = R.color.white;
    int hourColor = R.color.white;
    int minuteColor = R.color.white;
    int secondColor = R.color.red_crayola;
    float thickness = r.getDimensionPixelSize(R.dimen.default_hour_height);
    int hourWidth = r.getDimensionPixelSize(R.dimen.default_hour_width);
    int hourHeight = r.getDimensionPixelSize(R.dimen.default_hour_height);
    TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.AnalogClock);
    try {
      dialColor = attributes.getColor(R.styleable.AnalogClock_dial_color,
          ContextCompat.getColor(context, R.color.white));
      hourColor = attributes.getColor(R.styleable.AnalogClock_hour_color,
          ContextCompat.getColor(context, R.color.white));
      minuteColor = attributes.getColor(R.styleable.AnalogClock_minute_color,
          ContextCompat.getColor(context, R.color.white));
      secondColor = attributes.getColor(R.styleable.AnalogClock_second_color,
          ContextCompat.getColor(context, R.color.red_crayola));
      thickness = attributes.getDimensionPixelSize(R.styleable.AnalogClock_dial_thickness, -1);
      if (thickness == -1) {
        thickness = r.getDimension(R.dimen.default_dial_thickness);
      }
      hourWidth = attributes.getDimensionPixelSize(R.styleable.AnalogClock_hour_width,
          r.getDimensionPixelSize(R.dimen.default_hour_width));
      hourHeight = attributes.getDimensionPixelSize(R.styleable.AnalogClock_hour_height,
          r.getDimensionPixelSize(R.dimen.default_hour_height));
    } finally {
      attributes.recycle();
    }

    mCirclePaint = new Paint();
    mCirclePaint.setStyle(STROKE);
    mCirclePaint.setColor(dialColor);
    mCirclePaint.setAntiAlias(true);
    mCirclePaint.setStrokeWidth(thickness);

    GradientDrawable hourHand = (GradientDrawable) mHourHand.getDrawable(0);
    hourHand.setColor(hourColor);
    hourHand.setSize(hourWidth, hourHeight);

    ((GradientDrawable) mMinuteHand.getDrawable(0)).setColor(minuteColor);
    ((GradientDrawable) mSecondHand.getDrawable(0)).setColor(secondColor);

    mCalendar = new Time();
    mInternalPadding =
        (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, r.getDisplayMetrics());
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    if (!mAttached) {
      mAttached = true;
      IntentFilter filter = new IntentFilter();

      filter.addAction(Intent.ACTION_TIME_TICK);
      filter.addAction(Intent.ACTION_TIME_CHANGED);
      filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

      getContext().registerReceiver(mIntentReceiver, filter, null, mHandler);
    }

    // NOTE: It's safe to do these after registering the receiver since the receiver always runs
    // in the main thread, therefore the receiver can't run before this method returns.

    // The time zone may have changed while the receiver wasn't registered, so update the Time
    mCalendar = new Time();

    // Make sure we update to the current time
    onTimeChanged();

    // tick the seconds
    post(mClockTick);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (mAttached) {
      getContext().unregisterReceiver(mIntentReceiver);
      removeCallbacks(mClockTick);
      mAttached = false;
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int availableWidth = getWidth();
    int availableHeight = getHeight();

    mCenterX = availableWidth / 2;
    mCenterY = availableHeight / 2;

    // Choose the smaller between width and height to make a square
    int size = (availableWidth > availableHeight) ? availableHeight : availableWidth;
    mRadius = (size / 2) - mInternalPadding; // Radius of the outer circle
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    mChanged = true;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    boolean changed = mChanged;
    if (changed) {
      mChanged = false;
    }

    drawCircle(canvas, mCenterX, mCenterY, mRadius, mCirclePaint);
    drawHand(canvas, mHourHand, mCenterX, mCenterY, mHour / 12.0f * 360.0f, changed);
    drawHand(canvas, mMinuteHand, mCenterX, mCenterY, mMinutes / 60.0f * 360.0f, changed);
    if (!mNoSeconds) {
      drawHand(canvas, mSecondHand, mCenterX, mCenterY, mSeconds / 60.0f * 360.0f, changed);
    }
  }

  private void drawCircle(Canvas canvas, float x, float y, int radius, Paint paint) {
    canvas.drawCircle(x, y, radius, paint);
  }

  private void drawHand(Canvas canvas, Drawable hand, int x, int y, float angle, boolean changed) {
    canvas.save();
    canvas.rotate(angle, x, y);
    if (changed) {
      final int w = hand.getIntrinsicWidth();
      final int h = hand.getIntrinsicHeight();
      hand.setBounds(x - (w / 2), y - (h / 2), x + (w / 2), y + (h / 2));
    }
    hand.draw(canvas);
    canvas.restore();
  }

  private void onTimeChanged() {
    mCalendar.setToNow();

    if (mTimeZoneId != null) {
      mCalendar.switchTimezone(mTimeZoneId);
    }

    int hour = mCalendar.hour;
    int minute = mCalendar.minute;
    int second = mCalendar.second;
    //      long millis = System.currentTimeMillis() % 1000;

    mSeconds = second;//(float) ((second * 1000 + millis) / 166.666);
    mMinutes = minute + second / 60.0f;
    mHour = hour + mMinutes / 60.0f;
    mChanged = true;

    updateContentDescription(mCalendar);
  }

  private void updateContentDescription(Time time) {
    final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR;
    String contentDescription = DateUtils.formatDateTime(mContext, time.toMillis(false), flags);
    setContentDescription(contentDescription);
  }

  public void setTimeZone(String id) {
    mTimeZoneId = id;
    onTimeChanged();
  }

  public void enableSeconds(boolean enable) {
    mNoSeconds = !enable;
  }
}


