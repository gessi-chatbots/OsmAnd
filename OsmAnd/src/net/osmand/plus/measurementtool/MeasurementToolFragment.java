package net.osmand.plus.measurementtool;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import net.osmand.AndroidUtils;
import net.osmand.CallbackWithObject;
import net.osmand.IndexConstants;
import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.GPXUtilities;
import net.osmand.plus.GPXUtilities.GPXFile;
import net.osmand.plus.GPXUtilities.Route;
import net.osmand.plus.GPXUtilities.Track;
import net.osmand.plus.GPXUtilities.TrkSegment;
import net.osmand.plus.GPXUtilities.WptPt;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.IconsCache;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.TrackActivity;
import net.osmand.plus.activities.TrackActivity.NewGpxLine;
import net.osmand.plus.activities.TrackActivity.NewGpxLine.LineType;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.measurementtool.OptionsBottomSheetDialogFragment.OptionsFragmentListener;
import net.osmand.plus.measurementtool.SaveAsNewTrackBottomSheetDialogFragment.SaveAsNewTrackFragmentListener;
import net.osmand.plus.measurementtool.SelectedPointBottomSheetDialogFragment.SelectedPointFragmentListener;
import net.osmand.plus.measurementtool.SnapToRoadBottomSheetDialogFragment.SnapToRoadFragmentListener;
import net.osmand.plus.measurementtool.adapter.MeasurementToolAdapter;
import net.osmand.plus.measurementtool.adapter.MeasurementToolAdapter.MeasurementAdapterListener;
import net.osmand.plus.measurementtool.adapter.MeasurementToolItemTouchHelperCallback;
import net.osmand.plus.measurementtool.command.AddPointCommand;
import net.osmand.plus.measurementtool.command.ClearPointsCommand;
import net.osmand.plus.measurementtool.command.MeasurementCommandManager;
import net.osmand.plus.measurementtool.command.MovePointCommand;
import net.osmand.plus.measurementtool.command.RemovePointCommand;
import net.osmand.plus.measurementtool.command.ReorderPointCommand;
import net.osmand.plus.routing.RouteCalculationParams;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.routing.RouteProvider;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarController;
import net.osmand.router.RouteCalculationProgress;

import java.io.File;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static net.osmand.plus.OsmandSettings.LANDSCAPE_MIDDLE_RIGHT_CONSTANT;
import static net.osmand.plus.OsmandSettings.MIDDLE_TOP_CONSTANT;
import static net.osmand.plus.helpers.GpxImportHelper.GPX_SUFFIX;

public class MeasurementToolFragment extends Fragment {

	public static final String TAG = "MeasurementToolFragment";

	private final MeasurementCommandManager commandManager = new MeasurementCommandManager();
	private List<WptPt> measurementPoints = new LinkedList<>();
	private List<WptPt> snappedToRoadPoints = new LinkedList<>();
	private IconsCache iconsCache;
	private RecyclerView pointsRv;
	private String previousToolBarTitle = "";
	private MeasurementToolBarController toolBarController;
	private MeasurementToolAdapter adapter;
	private TextView distanceTv;
	private TextView pointsTv;
	private TextView distanceToCenterTv;
	private String pointsSt;
	private Drawable upIcon;
	private Drawable downIcon;
	private View pointsListContainer;
	private View upDownRow;
	private View mainView;
	private ImageView upDownBtn;
	private ImageView undoBtn;
	private ImageView redoBtn;
	private ImageView mainIcon;
	private SnapToRoadTask currentSnapToRoadTask;
	private ProgressBar snapToRoadProgressBar;

	private boolean wasCollapseButtonVisible;
	private boolean pointsListOpened;
	private Boolean saved;
	private boolean portrait;
	private boolean nightMode;
	private int previousMapPosition;
	private NewGpxLine newGpxLine;
	private boolean gpxPointsAdded;
	private boolean snapToRoadEnabled;
	private ApplicationMode snapToRoadAppMode;

	private boolean inMovePointMode;
	private boolean inAddPointAfterMode;
	private boolean inAddPointBeforeMode;

	private int selectedPointPos = -1;
	private WptPt selectedCachedPoint;

	private int positionToAddPoint = -1;

	private enum SaveType {
		ROUTE_POINT,
		LINE
	}

	public void setNewGpxLine(NewGpxLine newGpxLine) {
		this.newGpxLine = newGpxLine;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		final MapActivity mapActivity = (MapActivity) getActivity();
		final MeasurementToolLayer measurementLayer = mapActivity.getMapLayers().getMeasurementToolLayer();

		measurementLayer.setMeasurementPoints(measurementPoints);
		measurementLayer.setSnappedToRoadPoints(snappedToRoadPoints);
		if (selectedPointPos != -1 && selectedCachedPoint != null) {
			measurementLayer.setSelectedPointPos(selectedPointPos);
			measurementLayer.setSelectedCachedPoint(selectedCachedPoint);
		}

		// Handling screen rotation
		FragmentManager fragmentManager = mapActivity.getSupportFragmentManager();
		Fragment selectedPointFragment = fragmentManager.findFragmentByTag(SelectedPointBottomSheetDialogFragment.TAG);
		if (selectedPointFragment != null) {
			SelectedPointBottomSheetDialogFragment fragment = (SelectedPointBottomSheetDialogFragment) selectedPointFragment;
			fragment.setLineType(newGpxLine != null ? newGpxLine.getLineType() : null);
			fragment.setListener(createSelectedPointFragmentListener());
		}
		Fragment optionsFragment = fragmentManager.findFragmentByTag(OptionsBottomSheetDialogFragment.TAG);
		if (optionsFragment != null) {
			((OptionsBottomSheetDialogFragment) optionsFragment).setListener(createOptionsFragmentListener());
		}
		Fragment snapToRoadFragment = fragmentManager.findFragmentByTag(SnapToRoadBottomSheetDialogFragment.TAG);
		if (snapToRoadFragment != null) {
			((SnapToRoadBottomSheetDialogFragment) snapToRoadFragment).setListener(createSnapToRoadFragmentListener());
		}
		Fragment saveAsNewTrackFragment = mapActivity.getSupportFragmentManager().findFragmentByTag(SaveAsNewTrackBottomSheetDialogFragment.TAG);
		if (saveAsNewTrackFragment != null) {
			((SaveAsNewTrackBottomSheetDialogFragment) saveAsNewTrackFragment).setListener(createSaveAsNewTrackFragmentListener());
		}
		// If rotate the screen from landscape to portrait when the list of points is displayed then
		// the PointsListFragment will exist without view. This is necessary to remove it.
		if (portrait) {
			hidePointsListFragment();
		}

		commandManager.resetMeasurementLayer(measurementLayer);
		iconsCache = mapActivity.getMyApplication().getIconsCache();
		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		final int themeRes = nightMode ? R.style.OsmandDarkTheme : R.style.OsmandLightTheme;
		final int backgroundColor = ContextCompat.getColor(getActivity(),
				nightMode ? R.color.ctx_menu_info_view_bg_dark : R.color.ctx_menu_info_view_bg_light);
		portrait = AndroidUiHelper.isOrientationPortrait(getActivity());

		upIcon = getContentIcon(R.drawable.ic_action_arrow_up);
		downIcon = getContentIcon(R.drawable.ic_action_arrow_down);
		pointsSt = getString(R.string.points).toLowerCase();

		View view = View.inflate(new ContextThemeWrapper(getContext(), themeRes), R.layout.fragment_measurement_tool, null);

		mainView = view.findViewById(R.id.main_view);
		AndroidUtils.setBackground(mapActivity, mainView, nightMode, R.drawable.bg_bottom_menu_light, R.drawable.bg_bottom_menu_dark);
		pointsListContainer = view.findViewById(R.id.points_list_container);
		if (portrait && pointsListContainer != null) {
			pointsListContainer.setBackgroundColor(backgroundColor);
		}

		distanceTv = (TextView) mainView.findViewById(R.id.measurement_distance_text_view);
		pointsTv = (TextView) mainView.findViewById(R.id.measurement_points_text_view);
		distanceToCenterTv = (TextView) mainView.findViewById(R.id.distance_to_center_text_view);

		mainIcon = (ImageView) mainView.findViewById(R.id.main_icon);
		if (newGpxLine != null) {
			LineType lineType = newGpxLine.getLineType();
			if (lineType == LineType.ADD_SEGMENT || lineType == LineType.EDIT_SEGMENT) {
				mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_polygom_dark));
			} else {
				mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_markers_dark));
			}
		} else {
			mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_ruler));
		}

		upDownBtn = (ImageView) mainView.findViewById(R.id.up_down_button);
		upDownBtn.setImageDrawable(upIcon);

		mainView.findViewById(R.id.cancel_move_point_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (inMovePointMode) {
					cancelMovePointMode();
				}
			}
		});

		mainView.findViewById(R.id.cancel_point_before_after_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (inAddPointAfterMode) {
					cancelAddPointAfterMode();
				} else if (inAddPointBeforeMode) {
					cancelAddPointBeforeMode();
				}
			}
		});

		upDownRow = mainView.findViewById(R.id.up_down_row);
		upDownRow.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!pointsListOpened
						&& measurementLayer.getPointsCount() > 0
						&& !measurementLayer.isInMovePointMode()
						&& !measurementLayer.isInAddPointAfterMode()
						&& !measurementLayer.isInAddPointBeforeMode()) {
					showPointsList();
				} else {
					hidePointsList();
				}
			}
		});

		mainView.findViewById(R.id.apply_move_point_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (inMovePointMode) {
					applyMovePointMode();
				}
			}
		});

		mainView.findViewById(R.id.apply_point_before_after_point_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (inAddPointAfterMode) {
					applyAddPointAfterMode();
				} else if (inAddPointBeforeMode) {
					applyAddPointBeforeMode();
				}
			}
		});

		mainView.findViewById(R.id.add_point_before_after_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (inAddPointAfterMode) {
					addPointAfter();
				} else if (inAddPointBeforeMode) {
					addPointBefore();
				}
			}
		});

		mainView.findViewById(R.id.options_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				OptionsBottomSheetDialogFragment fragment = new OptionsBottomSheetDialogFragment();
				fragment.setSnapToRoadEnabled(snapToRoadEnabled);
				fragment.setListener(createOptionsFragmentListener());
				fragment.setAddLineMode(newGpxLine != null);
				fragment.show(mapActivity.getSupportFragmentManager(), OptionsBottomSheetDialogFragment.TAG);
			}
		});

		undoBtn = ((ImageButton) mainView.findViewById(R.id.undo_point_button));
		redoBtn = ((ImageButton) mainView.findViewById(R.id.redo_point_button));

		undoBtn.setImageDrawable(getContentIcon(R.drawable.ic_action_undo_dark));
		undoBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				commandManager.undo();
				if (commandManager.canUndo()) {
					enable(undoBtn);
				} else {
					disable(undoBtn);
				}
				hidePointsListIfNoPoints();
				if (measurementLayer.getPointsCount() > 0) {
					enable(upDownBtn);
				}
				adapter.notifyDataSetChanged();
				enable(redoBtn);
				updateText();
			}
		});

		redoBtn.setImageDrawable(getContentIcon(R.drawable.ic_action_redo_dark));
		redoBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				commandManager.redo();
				if (commandManager.canRedo()) {
					enable(redoBtn);
				} else {
					disable(redoBtn);
				}
				hidePointsListIfNoPoints();
				if (measurementLayer.getPointsCount() > 0) {
					enable(upDownBtn);
				}
				adapter.notifyDataSetChanged();
				enable(undoBtn);
				updateText();
			}
		});

		mainView.findViewById(R.id.add_point_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				addCenterPoint();
			}
		});

		measurementLayer.setOnSingleTapListener(new MeasurementToolLayer.OnSingleTapListener() {
			@Override
			public void onAddPoint() {
				addPoint();
			}

			@Override
			public void onSelectPoint(int selectedPointPos, WptPt selectedCachedPoint) {
				if (pointsListOpened) {
					hidePointsList();
				}
				MeasurementToolFragment.this.selectedPointPos = selectedPointPos;
				MeasurementToolFragment.this.selectedCachedPoint = selectedCachedPoint;
				if (selectedPointPos != -1 && selectedCachedPoint != null) {
					openSelectedPointMenu(mapActivity);
				}
			}
		});

		measurementLayer.setOnMeasureDistanceToCenterListener(new MeasurementToolLayer.OnMeasureDistanceToCenter() {
			@Override
			public void onMeasure(float distance) {
				String distStr = OsmAndFormatter.getFormattedDistance(distance, mapActivity.getMyApplication());
				distanceToCenterTv.setText(" – " + distStr);
			}
		});

		measurementLayer.setOnEnterMovePointModeListener(new MeasurementToolLayer.OnEnterMovePointModeListener() {
			@Override
			public void onEnterMovePointMode() {
				if (pointsListOpened) {
					hidePointsList();
				}
				switchMovePointMode(true);
			}
		});

		if (!commandManager.canUndo()) {
			disable(undoBtn);
		}
		if (!commandManager.canRedo()) {
			disable(redoBtn);
		}
		if (measurementLayer.getPointsCount() < 1) {
			disable(upDownBtn);
		}

		toolBarController = new MeasurementToolBarController(newGpxLine);
		if (inMovePointMode || inAddPointAfterMode || inAddPointBeforeMode) {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_mode_back, R.drawable.ic_action_mode_back);
		} else {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_remove_dark, R.drawable.ic_action_remove_dark);
		}
		if (newGpxLine != null) {
			LineType lineType = newGpxLine.getLineType();
			if (lineType == LineType.ADD_ROUTE_POINTS) {
				toolBarController.setTitle(getString(R.string.add_route_points));
			} else if (lineType == LineType.ADD_SEGMENT) {
				toolBarController.setTitle(getString(R.string.add_line));
			} else if (lineType == LineType.EDIT_SEGMENT) {
				toolBarController.setTitle(getString(R.string.edit_line));
			}
		} else {
			toolBarController.setTitle(getString(R.string.measurement_tool_action_bar));
		}
		toolBarController.setOnBackButtonClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				quit(false);
			}
		});
		toolBarController.setOnSaveViewClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (measurementLayer.getPointsCount() > 0) {
					addToGpx(mapActivity);
				} else {
					Toast.makeText(mapActivity, getString(R.string.none_point_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
		toolBarController.setOnSwitchCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
				if (!checked) {
					disableSnapToRoadMode();
				}
			}
		});
		mapActivity.showTopToolbar(toolBarController);

		adapter = new MeasurementToolAdapter(getMapActivity(), measurementLayer.getMeasurementPoints(),
				newGpxLine != null ? newGpxLine.getLineType() : null);
		if (portrait) {
			pointsRv = mainView.findViewById(R.id.measure_points_recycler_view);
		} else {
			pointsRv = new RecyclerView(getActivity());
		}
		final ItemTouchHelper touchHelper = new ItemTouchHelper(new MeasurementToolItemTouchHelperCallback(adapter));
		touchHelper.attachToRecyclerView(pointsRv);
		adapter.setAdapterListener(createMeasurementAdapterListener(touchHelper));
		pointsRv.setLayoutManager(new LinearLayoutManager(getContext()));
		pointsRv.setAdapter(adapter);

		enterMeasurementMode();

		if (snapToRoadEnabled) {
			enableSnapToRoadMode();
		}

		if (newGpxLine != null && !gpxPointsAdded) {
			LineType lineType = newGpxLine.getLineType();
			if (lineType == LineType.ADD_ROUTE_POINTS) {
				displayRoutePoints();
				gpxPointsAdded = true;
			} else if (lineType == LineType.EDIT_SEGMENT) {
				displaySegmentPoints();
				gpxPointsAdded = true;
			}
		}

		if (saved == null) {
			saved = newGpxLine != null && newGpxLine.getLineType() == LineType.ADD_ROUTE_POINTS;
		}

		return view;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		exitMeasurementMode();
		adapter.setAdapterListener(null);
		if (pointsListOpened) {
			hidePointsList();
		}
		if (inMovePointMode) {
			switchMovePointMode(false);
		}
		if (inAddPointAfterMode) {
			switchAddPointAfterMode(false);
		}
		if (inAddPointBeforeMode) {
			switchAddPointBeforeMode(false);
		}
		MeasurementToolLayer layer = getMeasurementLayer();
		if (layer != null) {
			layer.exitMovePointMode();
			layer.exitAddPointAfterMode();
			layer.exitAddPointBeforeMode();
			layer.setOnSingleTapListener(null);
			layer.setOnEnterMovePointModeListener(null);
		}
	}

	private MapActivity getMapActivity() {
		return (MapActivity) getActivity();
	}

	private MeasurementToolLayer getMeasurementLayer() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			return mapActivity.getMapLayers().getMeasurementToolLayer();
		}
		return null;
	}

	private Drawable getContentIcon(@DrawableRes int id) {
		return iconsCache.getIcon(id, nightMode ? R.color.ctx_menu_info_text_dark : R.color.icon_color);
	}

	private Drawable getActiveIcon(@DrawableRes int id) {
		return iconsCache.getIcon(id, nightMode ? R.color.osmand_orange : R.color.color_myloc_distance);
	}

	private void showSnapToRoadMenu(boolean rememberPreviousTitle) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (rememberPreviousTitle) {
				previousToolBarTitle = toolBarController.getTitle();
			}
			toolBarController.setTitle(getString(R.string.snap_to_road));
			mapActivity.refreshMap();
			SnapToRoadBottomSheetDialogFragment fragment = new SnapToRoadBottomSheetDialogFragment();
			fragment.setListener(createSnapToRoadFragmentListener());
			fragment.show(mapActivity.getSupportFragmentManager(), SnapToRoadBottomSheetDialogFragment.TAG);
		}
	}

	private OptionsFragmentListener createOptionsFragmentListener() {
		return new OptionsFragmentListener() {

			final MapActivity mapActivity = getMapActivity();
			final MeasurementToolLayer measurementLayer = getMeasurementLayer();

			@Override
			public void snapToRoadOnCLick() {
				if (!snapToRoadEnabled) {
					showSnapToRoadMenu(true);
				} else {
					disableSnapToRoadMode();
				}
			}

			@Override
			public void addToGpxOnClick() {
				if (mapActivity != null && measurementLayer != null) {
					if (measurementLayer.getPointsCount() > 0) {
						addToGpx(mapActivity);
					} else {
						Toast.makeText(mapActivity, getString(R.string.none_point_error), Toast.LENGTH_SHORT).show();
					}
				}
			}

			@Override
			public void saveAsNewTrackOnClick() {
				if (mapActivity != null && measurementLayer != null) {
					if (measurementLayer.getPointsCount() > 0) {
						openSaveAsNewTrackMenu(mapActivity);
					} else {
						Toast.makeText(mapActivity, getString(R.string.none_point_error), Toast.LENGTH_SHORT).show();
					}
				}
			}

			@Override
			public void addToTheTrackOnClick() {
				if (mapActivity != null && measurementLayer != null) {
					if (measurementLayer.getPointsCount() > 0) {
						showAddToTrackDialog(mapActivity);
					} else {
						Toast.makeText(mapActivity, getString(R.string.none_point_error), Toast.LENGTH_SHORT).show();
					}
				}
			}

			@Override
			public void clearAllOnClick() {
				commandManager.execute(new ClearPointsCommand(measurementLayer));
				if (pointsListOpened) {
					hidePointsList();
				}
				disable(redoBtn, upDownBtn);
				updateText();
				saved = false;
			}
		};
	}

	private SelectedPointFragmentListener createSelectedPointFragmentListener() {
		return new SelectedPointFragmentListener() {

			final MeasurementToolLayer measurementLayer = getMeasurementLayer();

			@Override
			public void moveOnClick() {
				if (measurementLayer != null) {
					measurementLayer.enterMovingPointMode();
				}
				switchMovePointMode(true);
			}

			@Override
			public void deleteOnClick() {
				clearSelection();
				if (measurementLayer != null) {
					int position = measurementLayer.getSelectedPointPos();
					commandManager.execute(new RemovePointCommand(measurementLayer, position));
					adapter.notifyDataSetChanged();
					disable(redoBtn);
					updateText();
					saved = false;
					hidePointsListIfNoPoints();
					measurementLayer.clearSelection();
				}
			}

			@Override
			public void addPointAfterOnClick() {
				if (measurementLayer != null) {
					positionToAddPoint = measurementLayer.getSelectedPointPos() + 1;
					measurementLayer.enterAddingPointAfterMode();
				}
				switchAddPointAfterMode(true);
			}

			@Override
			public void addPointBeforeOnClick() {
				if (measurementLayer != null) {
					positionToAddPoint = measurementLayer.getSelectedPointPos();
					measurementLayer.enterAddingPointBeforeMode();
				}
				switchAddPointBeforeMode(true);
			}

			@Override
			public void onCloseMenu() {
				setPreviousMapPosition();
			}
		};
	}

	private SnapToRoadFragmentListener createSnapToRoadFragmentListener() {
		return new SnapToRoadFragmentListener() {
			@Override
			public void onDestroyView(boolean snapToRoadEnabled) {
				if (!snapToRoadEnabled) {
					toolBarController.setTitle(previousToolBarTitle);
					MapActivity mapActivity = getMapActivity();
					if (mapActivity != null) {
						mapActivity.refreshMap();
					}
				}
			}

			@Override
			public void onApplicationModeItemClick(ApplicationMode mode) {
				snapToRoadAppMode = mode;
				enableSnapToRoadMode();
			}
		};
	}

	private SaveAsNewTrackFragmentListener createSaveAsNewTrackFragmentListener() {
		return new SaveAsNewTrackFragmentListener() {
			@Override
			public void saveAsRoutePointOnClick() {
				saveAsGpx(SaveType.ROUTE_POINT);
			}

			@Override
			public void saveAsLineOnClick() {
				saveAsGpx(SaveType.LINE);
			}
		};
	}

	private MeasurementAdapterListener createMeasurementAdapterListener(final ItemTouchHelper touchHelper) {
		return new MeasurementAdapterListener() {

			final MapActivity mapActivity = getMapActivity();
			final MeasurementToolLayer measurementLayer = getMeasurementLayer();
			private int fromPosition;
			private int toPosition;

			@Override
			public void onRemoveClick(int position) {
				if (measurementLayer != null) {
					commandManager.execute(new RemovePointCommand(measurementLayer, position));
					adapter.notifyDataSetChanged();
					disable(redoBtn);
					updateText();
					saved = false;
					hidePointsListIfNoPoints();
				}
			}

			@Override
			public void onItemClick(View view) {
				if (mapActivity != null && measurementLayer != null) {
					clearSelection();
					int position = pointsRv.indexOfChild(view);
					if (pointsListOpened) {
						hidePointsList();
					}
					OsmandMapTileView tileView = mapActivity.getMapView();
					if (portrait) {
						previousMapPosition = tileView.getMapPosition();
						tileView.setMapPosition(MIDDLE_TOP_CONSTANT);
					}
					mapActivity.refreshMap();
					measurementLayer.moveMapToPoint(position);
					measurementLayer.selectPoint(position);
				}
			}

			@Override
			public void onDragStarted(RecyclerView.ViewHolder holder) {
				fromPosition = holder.getAdapterPosition();
				touchHelper.startDrag(holder);
			}

			@Override
			public void onDragEnded(RecyclerView.ViewHolder holder) {
				if (mapActivity != null && measurementLayer != null) {
					toPosition = holder.getAdapterPosition();
					if (toPosition >= 0 && fromPosition >= 0 && toPosition != fromPosition) {
						commandManager.execute(new ReorderPointCommand(measurementLayer, fromPosition, toPosition));
						adapter.notifyDataSetChanged();
						disable(redoBtn);
						updateText();
						mapActivity.refreshMap();
						saved = false;
					}
				}
			}
		};
	}

	private void enableSnapToRoadMode() {
		if (snapToRoadAppMode != null) {
			toolBarController.setTopBarSwitchVisible(true);
			toolBarController.setTopBarSwitchChecked(true);
			snapToRoadEnabled = true;
			mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_snap_to_road));
			MapActivity mapActivity = getMapActivity();
			if (mapActivity != null) {
				ImageButton snapToRoadBtn = (ImageButton) mapActivity.findViewById(R.id.snap_to_road_image_button);
				snapToRoadBtn.setBackgroundResource(nightMode ? R.drawable.btn_circle_night : R.drawable.btn_circle);
				snapToRoadBtn.setImageDrawable(getActiveIcon(snapToRoadAppMode.getSmallIconDark()));
				snapToRoadBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						showSnapToRoadMenu(false);
					}
				});
				snapToRoadBtn.setVisibility(View.VISIBLE);

				if (snapToRoadProgressBar == null) {
					snapToRoadProgressBar = (ProgressBar) mainView.findViewById(R.id.snap_to_road_progress_bar);
					snapToRoadProgressBar.setMinimumHeight(0);
				}
				snapToRoadProgressBar.setVisibility(View.VISIBLE);
				snapToRoadProgressBar.setProgress(0);

				if (measurementPoints.size() > 1) {
					if (currentSnapToRoadTask != null && !currentSnapToRoadTask.isCancelled()) {
						currentSnapToRoadTask.cancel(true);
					}
					currentSnapToRoadTask = new SnapToRoadTask(mapActivity);
					currentSnapToRoadTask.execute();
				}

				mapActivity.refreshMap();
			}
		}
	}

	private void disableSnapToRoadMode() {
		toolBarController.setTopBarSwitchVisible(false);
		toolBarController.setTitle(previousToolBarTitle);
		snapToRoadEnabled = false;
		mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_ruler));
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.findViewById(R.id.snap_to_road_image_button).setVisibility(View.GONE);
			mainView.findViewById(R.id.snap_to_road_progress_bar).setVisibility(View.GONE);
			mapActivity.refreshMap();
		}
		if (currentSnapToRoadTask != null && !currentSnapToRoadTask.isCancelled()) {
			currentSnapToRoadTask.cancel(true);
		}
	}

	private void displayRoutePoints() {
		final MeasurementToolLayer measurementLayer = getMeasurementLayer();

		GPXFile gpx = newGpxLine.getGpxFile();
		List<WptPt> points = gpx.getRoutePoints();
		if (measurementLayer != null) {
			measurementPoints.addAll(points);
			adapter.notifyDataSetChanged();
			updateText();
		}
	}

	private void displaySegmentPoints() {
		final MeasurementToolLayer measurementLayer = getMeasurementLayer();

		TrkSegment segment = newGpxLine.getTrkSegment();
		List<WptPt> points = segment.points;
		if (measurementLayer != null) {
			measurementPoints.addAll(points);
			adapter.notifyDataSetChanged();
			updateText();
		}
	}

	private void openSelectedPointMenu(MapActivity mapActivity) {
		SelectedPointBottomSheetDialogFragment fragment = new SelectedPointBottomSheetDialogFragment();
		fragment.setLineType(newGpxLine != null ? newGpxLine.getLineType() : null);
		fragment.setListener(createSelectedPointFragmentListener());
		fragment.show(mapActivity.getSupportFragmentManager(), SelectedPointBottomSheetDialogFragment.TAG);
	}

	private void openSaveAsNewTrackMenu(MapActivity mapActivity) {
		SaveAsNewTrackBottomSheetDialogFragment fragment = new SaveAsNewTrackBottomSheetDialogFragment();
		fragment.setListener(createSaveAsNewTrackFragmentListener());
		fragment.show(mapActivity.getSupportFragmentManager(), SaveAsNewTrackBottomSheetDialogFragment.TAG);
	}

	private AlertDialog showAddToTrackDialog(final MapActivity mapActivity) {
		CallbackWithObject<GPXFile[]> callbackWithObject = new CallbackWithObject<GPXFile[]>() {
			@Override
			public boolean processResult(GPXFile[] result) {
				GPXFile gpxFile;
				if (result != null && result.length > 0) {
					gpxFile = result[0];
					SelectedGpxFile selectedGpxFile = mapActivity.getMyApplication().getSelectedGpxHelper().getSelectedFileByPath(gpxFile.path);
					boolean showOnMap = selectedGpxFile != null;
					saveExistingGpx(gpxFile, showOnMap, null, false);
				}
				return true;
			}
		};

		return GpxUiHelper.selectGPXFile(mapActivity, false, false, callbackWithObject);
	}

	private void applyMovePointMode() {
		if (inMovePointMode) {
			switchMovePointMode(false);
		}
		clearSelection();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			WptPt newPoint = measurementLayer.getMovedPointToApply();
			WptPt oldPoint = measurementLayer.getSelectedCachedPoint();
			int position = measurementLayer.getSelectedPointPos();
			commandManager.execute(new MovePointCommand(measurementLayer, oldPoint, newPoint, position));
			enable(undoBtn, upDownBtn);
			disable(redoBtn);
			updateText();
			adapter.notifyDataSetChanged();
			saved = false;
			measurementLayer.exitMovePointMode();
			measurementLayer.clearSelection();
			measurementLayer.refreshMap();
		}
	}

	private void cancelMovePointMode() {
		if (inMovePointMode) {
			switchMovePointMode(false);
		}
		clearSelection();
		MeasurementToolLayer measurementToolLayer = getMeasurementLayer();
		if (measurementToolLayer != null) {
			measurementToolLayer.exitMovePointMode();
			measurementToolLayer.clearSelection();
			measurementToolLayer.refreshMap();
		}
	}

	private void addPointAfter() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null && positionToAddPoint != -1) {
			if (addPointToPosition(positionToAddPoint)) {
				selectedPointPos += 1;
				selectedCachedPoint = new WptPt(measurementLayer.getMeasurementPoints().get(selectedPointPos));
				measurementLayer.setSelectedPointPos(selectedPointPos);
				measurementLayer.setSelectedCachedPoint(selectedCachedPoint);
				measurementLayer.refreshMap();
				positionToAddPoint += 1;
			}
		}
	}

	private void applyAddPointAfterMode() {
		if (inAddPointAfterMode) {
			switchAddPointAfterMode(false);
		}
		clearSelection();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			measurementLayer.exitAddPointAfterMode();
			measurementLayer.clearSelection();
			measurementLayer.refreshMap();
		}
		positionToAddPoint = -1;
	}

	private void cancelAddPointAfterMode() {
		if (inAddPointAfterMode) {
			switchAddPointAfterMode(false);
		}
		clearSelection();
		MeasurementToolLayer measurementToolLayer = getMeasurementLayer();
		if (measurementToolLayer != null) {
			measurementToolLayer.exitAddPointAfterMode();
			measurementToolLayer.clearSelection();
			measurementToolLayer.refreshMap();
		}
		positionToAddPoint = -1;
	}

	private void addPointBefore() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null && positionToAddPoint != -1) {
			if (addPointToPosition(positionToAddPoint)) {
				selectedCachedPoint = new WptPt(measurementLayer.getMeasurementPoints().get(selectedPointPos));
				measurementLayer.setSelectedPointPos(selectedPointPos);
				measurementLayer.setSelectedCachedPoint(selectedCachedPoint);
				measurementLayer.refreshMap();
			}
		}
	}

	private void applyAddPointBeforeMode() {
		if (inAddPointBeforeMode) {
			switchAddPointBeforeMode(false);
		}
		clearSelection();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			measurementLayer.exitAddPointBeforeMode();
			measurementLayer.clearSelection();
			measurementLayer.refreshMap();
		}
		positionToAddPoint = -1;
	}

	private void cancelAddPointBeforeMode() {
		if (inAddPointBeforeMode) {
			switchAddPointBeforeMode(false);
		}
		clearSelection();
		MeasurementToolLayer measurementToolLayer = getMeasurementLayer();
		if (measurementToolLayer != null) {
			measurementToolLayer.exitAddPointBeforeMode();
			measurementToolLayer.clearSelection();
			measurementToolLayer.refreshMap();
		}
		positionToAddPoint = -1;
	}

	private void clearSelection() {
		selectedPointPos = -1;
		selectedCachedPoint = null;
	}

	private void switchMovePointMode(boolean enable) {
		inMovePointMode = enable;
		if (inMovePointMode) {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_mode_back, R.drawable.ic_action_mode_back);
		} else {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_remove_dark, R.drawable.ic_action_remove_dark);
		}
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.showTopToolbar(toolBarController);
		}
		markGeneralComponents(enable ? View.GONE : View.VISIBLE);
		mark(enable ? View.VISIBLE : View.GONE,
				R.id.move_point_text,
				R.id.move_point_controls);
		mainIcon.setImageDrawable(getActiveIcon(enable
				? R.drawable.ic_action_move_point
				: R.drawable.ic_action_ruler));
	}

	private void switchAddPointAfterMode(boolean enable) {
		inAddPointAfterMode = enable;
		if (inAddPointAfterMode) {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_mode_back, R.drawable.ic_action_mode_back);
		} else {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_remove_dark, R.drawable.ic_action_remove_dark);
		}
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.showTopToolbar(toolBarController);
		}
		markGeneralComponents(enable ? View.GONE : View.VISIBLE);
		mark(enable ? View.VISIBLE : View.GONE,
				R.id.add_point_after_text,
				R.id.add_point_before_after_controls);
		mainIcon.setImageDrawable(getActiveIcon(enable
				? R.drawable.ic_action_addpoint_above
				: R.drawable.ic_action_ruler));
	}

	private void switchAddPointBeforeMode(boolean enable) {
		inAddPointBeforeMode = enable;
		if (inAddPointBeforeMode) {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_mode_back, R.drawable.ic_action_mode_back);
		} else {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_remove_dark, R.drawable.ic_action_remove_dark);
		}
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.showTopToolbar(toolBarController);
		}
		markGeneralComponents(enable ? View.GONE : View.VISIBLE);
		mark(enable ? View.VISIBLE : View.GONE,
				R.id.add_point_before_text,
				R.id.add_point_before_after_controls);
		mainIcon.setImageDrawable(getActiveIcon(enable
				? R.drawable.ic_action_addpoint_below
				: R.drawable.ic_action_ruler));
	}

	private void markGeneralComponents(int status) {
		mark(status,
				R.id.measurement_distance_text_view,
				R.id.measurement_points_text_view,
				R.id.distance_to_center_text_view,
				R.id.up_down_button,
				R.id.measure_mode_controls);
	}

	private void addPoint() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			commandManager.execute(new AddPointCommand(measurementLayer, false));
			enable(undoBtn, upDownBtn);
			disable(redoBtn);
			updateText();
			adapter.notifyDataSetChanged();
			saved = false;
		}
	}

	private void addCenterPoint() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			commandManager.execute(new AddPointCommand(measurementLayer, true));
			enable(undoBtn, upDownBtn);
			disable(redoBtn);
			updateText();
			adapter.notifyDataSetChanged();
			saved = false;
		}
	}

	private boolean addPointToPosition(int position) {
		boolean added = false;
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			added = commandManager.execute(new AddPointCommand(measurementLayer, position));
			enable(undoBtn, upDownBtn);
			disable(redoBtn);
			updateText();
			adapter.notifyDataSetChanged();
			saved = false;
		}
		return added;
	}

	private void showPointsList() {
		pointsListOpened = true;
		upDownBtn.setImageDrawable(downIcon);
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (portrait && pointsListContainer != null) {
				pointsListContainer.setVisibility(View.VISIBLE);
			} else {
				showPointsListFragment();
			}
			OsmandMapTileView tileView = mapActivity.getMapView();
			previousMapPosition = tileView.getMapPosition();
			if (portrait) {
				tileView.setMapPosition(MIDDLE_TOP_CONSTANT);
			} else {
				tileView.setMapPosition(LANDSCAPE_MIDDLE_RIGHT_CONSTANT);
			}
			mapActivity.refreshMap();
		}
	}

	private void hidePointsList() {
		pointsListOpened = false;
		upDownBtn.setImageDrawable(upIcon);
		if (portrait && pointsListContainer != null) {
			pointsListContainer.setVisibility(View.GONE);
		} else {
			hidePointsListFragment();
		}
		setPreviousMapPosition();
	}

	private void hidePointsListIfNoPoints() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			if (measurementLayer.getPointsCount() < 1) {
				disable(upDownBtn);
				if (pointsListOpened) {
					hidePointsList();
				}
			}
		}
	}

	private void showPointsListFragment() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			int screenHeight = AndroidUtils.getScreenHeight(mapActivity) - AndroidUtils.getStatusBarHeight(mapActivity);
			MeasurePointsListFragment fragment = new MeasurePointsListFragment();
			fragment.setRecyclerView(pointsRv);
			fragment.setWidth(upDownRow.getWidth());
			fragment.setHeight(screenHeight - upDownRow.getHeight());
			mapActivity.getSupportFragmentManager().beginTransaction()
					.add(R.id.fragmentContainer, fragment, MeasurePointsListFragment.TAG)
					.commitAllowingStateLoss();
		}
	}

	private void hidePointsListFragment() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			try {
				FragmentManager manager = mapActivity.getSupportFragmentManager();
				Fragment fragment = manager.findFragmentByTag(MeasurePointsListFragment.TAG);
				if (fragment != null) {
					manager.beginTransaction().remove(fragment).commitAllowingStateLoss();
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}

	private void setPreviousMapPosition() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapView().setMapPosition(previousMapPosition);
			mapActivity.refreshMap();
		}
	}

	private void addToGpx(MapActivity mapActivity) {
		GPXFile gpx = newGpxLine.getGpxFile();
		SelectedGpxFile selectedGpxFile = mapActivity.getMyApplication().getSelectedGpxHelper().getSelectedFileByPath(gpx.path);
		boolean showOnMap = selectedGpxFile != null;
		LineType lineType = newGpxLine.getLineType();
		saveExistingGpx(gpx, showOnMap, lineType, true);
	}

	private void saveAsGpx(final SaveType saveType) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			final File dir = mapActivity.getMyApplication().getAppPath(IndexConstants.GPX_INDEX_DIR);
			final LayoutInflater inflater = mapActivity.getLayoutInflater();
			final View view = inflater.inflate(R.layout.save_gpx_dialog, null);
			final EditText nameEt = (EditText) view.findViewById(R.id.gpx_name_et);
			final TextView fileExistsTv = (TextView) view.findViewById(R.id.file_exists_text_view);
			final SwitchCompat showOnMapToggle = (SwitchCompat) view.findViewById(R.id.toggle_show_on_map);
			showOnMapToggle.setChecked(true);

			final String suggestedName = new SimpleDateFormat("yyyy-M-dd_HH-mm_EEE", Locale.US).format(new Date());
			String displayedName = suggestedName;
			File fout = new File(dir, suggestedName + GPX_SUFFIX);
			int ind = 1;
			while (fout.exists()) {
				displayedName = suggestedName + "_" + (++ind);
				fout = new File(dir, displayedName + GPX_SUFFIX);
			}
			nameEt.setText(displayedName);
			nameEt.setSelection(displayedName.length());

			final boolean[] textChanged = new boolean[1];
			nameEt.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

				}

				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

				}

				@Override
				public void afterTextChanged(Editable editable) {
					if (new File(dir, editable.toString() + GPX_SUFFIX).exists()) {
						fileExistsTv.setVisibility(View.VISIBLE);
					} else {
						fileExistsTv.setVisibility(View.INVISIBLE);
					}
					textChanged[0] = true;
				}
			});

			new AlertDialog.Builder(mapActivity)
					.setTitle(R.string.enter_gpx_name)
					.setView(view)
					.setPositiveButton(R.string.shared_string_save, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							final String name = nameEt.getText().toString();
							String fileName = name + GPX_SUFFIX;
							if (textChanged[0]) {
								File fout = new File(dir, fileName);
								int ind = 1;
								while (fout.exists()) {
									fileName = name + "_" + (++ind) + GPX_SUFFIX;
									fout = new File(dir, fileName);
								}
							}
							saveNewGpx(dir, fileName, showOnMapToggle.isChecked(), saveType);
						}
					})
					.setNegativeButton(R.string.shared_string_cancel, null)
					.show();
		}
	}

	private void saveNewGpx(File dir, String fileName, boolean checked, SaveType saveType) {
		saveGpx(dir, fileName, checked, null, false, null, saveType);
	}

	private void saveExistingGpx(GPXFile gpx, boolean showOnMap, LineType lineType, boolean openTrackActivity) {
		saveGpx(null, null, showOnMap, gpx, openTrackActivity, lineType, null);
	}

	private void saveGpx(final File dir,
						 final String fileName,
						 final boolean showOnMap,
						 final GPXFile gpx,
						 final boolean openTrackActivity,
						 final LineType lineType,
						 final SaveType saveType) {

		new AsyncTask<Void, Void, String>() {

			private ProgressDialog progressDialog;
			private File toSave;

			@Override
			protected void onPreExecute() {
				MapActivity activity = getMapActivity();
				if (activity != null) {
					progressDialog = new ProgressDialog(activity);
					progressDialog.setMessage(getString(R.string.saving_gpx_tracks));
					progressDialog.show();
				}
			}

			@Override
			protected String doInBackground(Void... voids) {
				MeasurementToolLayer measurementLayer = getMeasurementLayer();
				MapActivity activity = getMapActivity();
				if (gpx == null) {
					toSave = new File(dir, fileName);
					GPXFile gpx = new GPXFile();
					if (measurementLayer != null) {
						List<WptPt> points = measurementLayer.getMeasurementPoints();
						if (saveType == SaveType.LINE) {
							TrkSegment segment = new TrkSegment();
							segment.points.addAll(points);
							Track track = new Track();
							track.segments.add(segment);
							gpx.tracks.add(track);
						} else if (saveType == SaveType.ROUTE_POINT) {
							Route rt = new Route();
							gpx.routes.add(rt);
							rt.points.addAll(points);
						}
					}
					if (activity != null) {
						String res = GPXUtilities.writeGpxFile(toSave, gpx, activity.getMyApplication());
						gpx.path = toSave.getAbsolutePath();
						if (showOnMap) {
							activity.getMyApplication().getSelectedGpxHelper().selectGpxFile(gpx, true, false);
						}
						return res;
					}
				} else {
					toSave = new File(gpx.path);
					if (measurementLayer != null) {
						List<WptPt> points = measurementLayer.getMeasurementPoints();
						if (lineType != null) {
							switch (lineType) {
								case ADD_SEGMENT:
									gpx.addTrkSegment(points);
									break;
								case ADD_ROUTE_POINTS:
									gpx.replaceRoutePoints(points);
									break;
								case EDIT_SEGMENT:
									TrkSegment segment = new TrkSegment();
									segment.points.addAll(points);
									gpx.replaceSegment(newGpxLine.getTrkSegment(), segment);
									break;
							}
						} else {
							gpx.addRoutePoints(points);
						}
					}
					if (activity != null) {
						String res = GPXUtilities.writeGpxFile(toSave, gpx, activity.getMyApplication());
						if (showOnMap) {
							SelectedGpxFile sf = activity.getMyApplication().getSelectedGpxHelper().selectGpxFile(gpx, true, false);
							if (sf != null) {
								if (lineType == LineType.ADD_SEGMENT || lineType == LineType.EDIT_SEGMENT) {
									sf.processPoints();
								}
							}
						}
						return res;
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(String warning) {
				MapActivity activity = getMapActivity();
				if (progressDialog != null && progressDialog.isShowing()) {
					progressDialog.dismiss();
				}
				if (activity != null) {
					activity.refreshMap();
					if (warning == null) {
						saved = true;
						if (openTrackActivity) {
							dismiss(activity);
						} else {
							Toast.makeText(activity,
									MessageFormat.format(getString(R.string.gpx_saved_sucessfully), toSave.getAbsolutePath()),
									Toast.LENGTH_LONG).show();
						}
					} else {
						Toast.makeText(activity, warning, Toast.LENGTH_LONG).show();
					}
				}
			}
		}.execute();
	}

	private void enable(View... views) {
		for (View view : views) {
			view.setEnabled(true);
			view.setAlpha(1);
		}
	}

	private void disable(View... views) {
		for (View view : views) {
			view.setEnabled(false);
			view.setAlpha(.5f);
		}
	}

	private void updateText() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			distanceTv.setText(measurementLayer.getDistanceSt() + ",");
			pointsTv.setText((portrait ? pointsSt + ": " : "") + measurementLayer.getPointsCount());
		}
	}

	private void enterMeasurementMode() {
		MapActivity mapActivity = getMapActivity();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (mapActivity != null && measurementLayer != null) {
			measurementLayer.setInMeasurementMode(true);
			mapActivity.refreshMap();
			mapActivity.disableDrawer();

			mark(portrait ? View.INVISIBLE : View.GONE,
					R.id.map_left_widgets_panel,
					R.id.map_right_widgets_panel,
					R.id.map_center_info);
			mark(View.GONE,
					R.id.map_route_info_button,
					R.id.map_menu_button,
					R.id.map_compass_button,
					R.id.map_layers_button,
					R.id.map_search_button,
					R.id.map_quick_actions_button);

			View collapseButton = mapActivity.findViewById(R.id.map_collapse_button);
			if (collapseButton != null && collapseButton.getVisibility() == View.VISIBLE) {
				wasCollapseButtonVisible = true;
				collapseButton.setVisibility(View.INVISIBLE);
			} else {
				wasCollapseButtonVisible = false;
			}

			updateText();
		}
	}

	private void exitMeasurementMode() {
		MapActivity mapActivity = getMapActivity();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (mapActivity != null && measurementLayer != null) {
			if (toolBarController != null) {
				mapActivity.hideTopToolbar(toolBarController);
			}
			measurementLayer.setInMeasurementMode(false);
			mapActivity.enableDrawer();

			mark(View.VISIBLE,
					R.id.map_left_widgets_panel,
					R.id.map_right_widgets_panel,
					R.id.map_center_info,
					R.id.map_route_info_button,
					R.id.map_menu_button,
					R.id.map_compass_button,
					R.id.map_layers_button,
					R.id.map_search_button,
					R.id.map_quick_actions_button);

			View collapseButton = mapActivity.findViewById(R.id.map_collapse_button);
			if (collapseButton != null && wasCollapseButtonVisible) {
				collapseButton.setVisibility(View.VISIBLE);
			}

			mapActivity.refreshMap();
		}
	}

	private void mark(int status, int... widgets) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			for (int widget : widgets) {
				View v = mapActivity.findViewById(widget);
				if (v != null) {
					v.setVisibility(status);
				}
			}
		}
	}

	public void quit(boolean hidePointsListFirst) {
		if (inMovePointMode) {
			cancelMovePointMode();
			return;
		}
		if (inAddPointAfterMode) {
			cancelAddPointAfterMode();
			return;
		}
		if (inAddPointBeforeMode) {
			cancelAddPointBeforeMode();
			return;
		}
		showQuitDialog(hidePointsListFirst);
	}

	private void showQuitDialog(boolean hidePointsListFirst) {
		final MapActivity mapActivity = getMapActivity();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (mapActivity != null && measurementLayer != null) {
			if (pointsListOpened && hidePointsListFirst) {
				hidePointsList();
				return;
			}
			if (measurementLayer.getPointsCount() < 1 || saved) {
				dismiss(mapActivity);
				return;
			}
			new AlertDialog.Builder(mapActivity)
					.setTitle(getString(R.string.are_you_sure))
					.setMessage(getString(R.string.unsaved_changes_will_be_lost))
					.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dismiss(mapActivity);
						}
					})
					.setNegativeButton(R.string.shared_string_cancel, null)
					.show();
		}
	}

	private void dismiss(MapActivity mapActivity) {
		try {
			MeasurementToolLayer measurementToolLayer = getMeasurementLayer();
			if (measurementToolLayer != null) {
				measurementToolLayer.getMeasurementPoints().clear();
			}
			if (pointsListOpened) {
				hidePointsList();
			}
			if (snapToRoadEnabled) {
				disableSnapToRoadMode();
			}
			if (newGpxLine != null) {
				GPXFile gpx = newGpxLine.getGpxFile();
				Intent newIntent = new Intent(mapActivity, mapActivity.getMyApplication().getAppCustomization().getTrackActivity());
				newIntent.putExtra(TrackActivity.TRACK_FILE_NAME, gpx.path);
				newIntent.putExtra(TrackActivity.OPEN_TRACKS_LIST, true);
				newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(newIntent);
			}
			mapActivity.getSupportFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
		} catch (Exception e) {
			// ignore
		}
	}

	public static boolean showInstance(FragmentManager fragmentManager, NewGpxLine newGpxLine) {
		try {
			MeasurementToolFragment fragment = new MeasurementToolFragment();
			fragment.setNewGpxLine(newGpxLine);
			fragment.setRetainInstance(true);
			fragmentManager.beginTransaction()
					.add(R.id.bottomFragmentContainer, fragment, MeasurementToolFragment.TAG)
					.commitAllowingStateLoss();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private class SnapToRoadTask extends AsyncTask<Void, Void, RouteCalculationResult> {

		private MapActivity mapActivity;
		private boolean calculated;

		SnapToRoadTask(MapActivity mapActivity) {
			this.mapActivity = mapActivity;
		}

		@Override
		protected RouteCalculationResult doInBackground(Void... voids) {
			OsmandApplication app = mapActivity.getMyApplication();
			OsmandSettings settings = app.getSettings();
			RouteCalculationParams params = new RouteCalculationParams();

			Location start = new Location("");
			WptPt first = measurementPoints.get(0);
			start.setLatitude(first.getLatitude());
			start.setLongitude(first.getLongitude());

			WptPt last = measurementPoints.get(measurementPoints.size() - 1);
			LatLon end = new LatLon(last.getLatitude(), last.getLongitude());

			params.start = start;
			params.end = end;
			params.leftSide = settings.DRIVING_REGION.get().leftHandDriving;
			params.fast = settings.FAST_ROUTE_MODE.getModeValue(snapToRoadAppMode);
			params.type = settings.ROUTER_SERVICE.getModeValue(snapToRoadAppMode);
			params.mode = snapToRoadAppMode;
			params.ctx = app;
			params.calculationProgress = new RouteCalculationProgress();

			List<LatLon> intermediates = new ArrayList<>();
			if (measurementPoints.size() > 2) {
				for (int i = 1; i < measurementPoints.size() - 1; i++) {
					WptPt pt = measurementPoints.get(i);
					intermediates.add(new LatLon(pt.getLatitude(), pt.getLongitude()));
				}
				params.intermediates = intermediates;
			}

			updateProgress(params.calculationProgress);

			return new RouteProvider().calculateRouteImpl(params);
		}

		private void updateProgress(final RouteCalculationProgress progress) {
			mapActivity.getMyApplication().runInUIThread(new Runnable() {

				@Override
				public void run() {
					float p = Math.max(progress.distanceFromBegin, progress.distanceFromEnd);
					float all = progress.totalEstimatedDistance * 1.25f;
					if (all > 0) {
						int t = (int) Math.min(p * p / (all * all) * 100f, 99);
						snapToRoadProgressBar.setProgress(t);
					}
					if (!calculated && !isCancelled()) {
						updateProgress(progress);
					}
				}
			}, 100);
		}

		@Override
		protected void onPostExecute(RouteCalculationResult result) {
			calculated = true;
			snappedToRoadPoints.clear();
			for (Location loc : result.getRouteLocations()) {
				WptPt pt = new WptPt();
				pt.lat = loc.getLatitude();
				pt.lon = loc.getLongitude();
				snappedToRoadPoints.add(pt);
			}
			mapActivity.refreshMap();
			super.onPostExecute(result);
		}
	}

	private class MeasurementToolBarController extends TopToolbarController {

		MeasurementToolBarController(NewGpxLine newGpxLine) {
			super(MapInfoWidgetsFactory.TopToolbarControllerType.MEASUREMENT_TOOL);
			setBackBtnIconClrIds(0, 0);
			setTitleTextClrIds(R.color.primary_text_dark, R.color.primary_text_dark);
			setDescrTextClrIds(R.color.primary_text_dark, R.color.primary_text_dark);
			setBgIds(R.drawable.gradient_toolbar, R.drawable.gradient_toolbar,
					R.drawable.gradient_toolbar, R.drawable.gradient_toolbar);
			setCloseBtnVisible(false);
			if (newGpxLine != null) {
				setSaveViewVisible(true);
			}
			setSingleLineTitle(false);
		}

		@Override
		public void updateToolbar(MapInfoWidgetsFactory.TopToolbarView view) {
			super.updateToolbar(view);
			View shadow = view.getShadowView();
			if (shadow != null) {
				shadow.setVisibility(View.GONE);
			}
		}
	}
}
