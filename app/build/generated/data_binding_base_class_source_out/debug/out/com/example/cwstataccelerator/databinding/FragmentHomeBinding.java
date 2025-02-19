// Generated by view binder compiler. Do not edit!
package com.example.cwstataccelerator.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.example.cwstataccelerator.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class FragmentHomeBinding implements ViewBinding {
  @NonNull
  private final ScrollView rootView;

  @NonNull
  public final TextView bucketSummary;

  @NonNull
  public final TextView databaseStats;

  @NonNull
  public final TextView databaseStatusMessage;

  @NonNull
  public final ProgressBar databaseUpdateProgress;

  @NonNull
  public final TextView textHome;

  private FragmentHomeBinding(@NonNull ScrollView rootView, @NonNull TextView bucketSummary,
      @NonNull TextView databaseStats, @NonNull TextView databaseStatusMessage,
      @NonNull ProgressBar databaseUpdateProgress, @NonNull TextView textHome) {
    this.rootView = rootView;
    this.bucketSummary = bucketSummary;
    this.databaseStats = databaseStats;
    this.databaseStatusMessage = databaseStatusMessage;
    this.databaseUpdateProgress = databaseUpdateProgress;
    this.textHome = textHome;
  }

  @Override
  @NonNull
  public ScrollView getRoot() {
    return rootView;
  }

  @NonNull
  public static FragmentHomeBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static FragmentHomeBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.fragment_home, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static FragmentHomeBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.bucket_summary;
      TextView bucketSummary = ViewBindings.findChildViewById(rootView, id);
      if (bucketSummary == null) {
        break missingId;
      }

      id = R.id.database_stats;
      TextView databaseStats = ViewBindings.findChildViewById(rootView, id);
      if (databaseStats == null) {
        break missingId;
      }

      id = R.id.database_status_message;
      TextView databaseStatusMessage = ViewBindings.findChildViewById(rootView, id);
      if (databaseStatusMessage == null) {
        break missingId;
      }

      id = R.id.database_update_progress;
      ProgressBar databaseUpdateProgress = ViewBindings.findChildViewById(rootView, id);
      if (databaseUpdateProgress == null) {
        break missingId;
      }

      id = R.id.text_home;
      TextView textHome = ViewBindings.findChildViewById(rootView, id);
      if (textHome == null) {
        break missingId;
      }

      return new FragmentHomeBinding((ScrollView) rootView, bucketSummary, databaseStats,
          databaseStatusMessage, databaseUpdateProgress, textHome);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
