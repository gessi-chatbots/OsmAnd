package net.osmand.plus.measurementtool;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.ChartData;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.AndroidUtils;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.helpers.GpxUiHelper.LineGraphType;
import net.osmand.plus.helpers.GpxUiHelper.GPXDataSetAxisType;
import net.osmand.plus.helpers.GpxUiHelper.OrderedLineDataSet;
import net.osmand.plus.mapcontextmenu.other.HorizontalSelectionAdapter;
import net.osmand.plus.mapcontextmenu.other.HorizontalSelectionAdapter.HorizontalSelectionAdapterListener;
import net.osmand.plus.mapcontextmenu.other.TrackDetailsMenu;
import net.osmand.plus.measurementtool.MeasurementToolFragment.OnUpdateAdditionalInfoListener;
import net.osmand.plus.measurementtool.graph.CommonGraphAdapter;
import net.osmand.plus.measurementtool.graph.CustomGraphAdapter;
import net.osmand.plus.measurementtool.graph.CustomGraphAdapter.LegendViewType;
import net.osmand.plus.measurementtool.graph.BaseGraphAdapter;
import net.osmand.plus.measurementtool.graph.GraphAdapterHelper;
import net.osmand.plus.routepreparationmenu.RouteDetailsFragment;
import net.osmand.plus.routepreparationmenu.cards.BaseCard;
import net.osmand.router.RouteSegmentResult;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.osmand.GPXUtilities.GPXFile;
import static net.osmand.GPXUtilities.GPXTrackAnalysis;
import static net.osmand.plus.mapcontextmenu.other.HorizontalSelectionAdapter.HorizontalSelectionItem;
import static net.osmand.router.RouteStatisticsHelper.RouteStatistics;

public class GraphsCard extends BaseCard implements OnUpdateAdditionalInfoListener {

	private static String GRAPH_DATA_GPX_FILE_NAME = "graph_data_tmp";
	private static int INVALID_ID = -1;

	private MeasurementEditingContext editingCtx;
	private MeasurementToolFragment fragment;

	private GraphType visibleType;
	private List<GraphType> graphTypes = new ArrayList<>();
	private GpxDisplayItem gpxItem;

	private View commonGraphContainer;
	private View customGraphContainer;
	private View messageContainer;
	private CommonGraphAdapter commonGraphAdapter;
	private CustomGraphAdapter customGraphAdapter;
	private RecyclerView graphTypesMenu;

	private TrackDetailsMenu trackDetailsMenu;

	private enum CommonGraphType {
		OVERVIEW(R.string.shared_string_overview, true, LineGraphType.ALTITUDE, LineGraphType.SLOPE),
		ALTITUDE(R.string.altitude, true, LineGraphType.ALTITUDE),
		SLOPE(R.string.shared_string_slope, true, LineGraphType.SLOPE),
		SPEED(R.string.map_widget_speed, false, LineGraphType.SPEED);

		CommonGraphType(int titleId, boolean canBeCalculated, LineGraphType ... lineGraphTypes) {
			this.titleId = titleId;
			this.canBeCalculated = canBeCalculated;
			this.lineGraphTypes = lineGraphTypes;
		}

		final int titleId;
		final boolean canBeCalculated;
		final LineGraphType[] lineGraphTypes;
	}

	public GraphsCard(@NonNull MapActivity mapActivity,
	                  TrackDetailsMenu trackDetailsMenu,
	                  MeasurementToolFragment fragment) {
		super(mapActivity);
		this.trackDetailsMenu = trackDetailsMenu;
		this.fragment = fragment;
	}

	@Override
	protected void updateContent() {
		if (mapActivity == null || fragment == null) return;
		editingCtx = fragment.getEditingCtx();

		graphTypesMenu = view.findViewById(R.id.graph_types_recycler_view);
		graphTypesMenu.setLayoutManager(new LinearLayoutManager(mapActivity, RecyclerView.HORIZONTAL, false));
		commonGraphContainer = view.findViewById(R.id.common_graphs_container);
		customGraphContainer = view.findViewById(R.id.custom_graphs_container);
		messageContainer = view.findViewById(R.id.message_container);
		LineChart lineChart = (LineChart) view.findViewById(R.id.line_chart);
		HorizontalBarChart barChart = (HorizontalBarChart) view.findViewById(R.id.horizontal_chart);
		commonGraphAdapter = new CommonGraphAdapter(lineChart, true);
		customGraphAdapter = new CustomGraphAdapter(barChart, true);

		customGraphAdapter.setLegendContainer((ViewGroup) view.findViewById(R.id.route_legend));
		customGraphAdapter.setLayoutChangeListener(new BaseGraphAdapter.LayoutChangeListener() {
			@Override
			public void onLayoutChanged() {
				setLayoutNeeded();
			}
		});

		GraphAdapterHelper.bindGraphAdapters(commonGraphAdapter, Arrays.asList((BaseGraphAdapter) customGraphAdapter), (ViewGroup) view);
		GraphAdapterHelper.bindToMap(commonGraphAdapter, mapActivity, trackDetailsMenu);
		fullUpdate();
	}

	@Override
	public int getCardLayoutId() {
		return R.layout.fragment_measurement_tool_graph;
	}

	@Override
	public void onUpdateAdditionalInfo() {
		fullUpdate();
	}

	private void fullUpdate() {
		if (!isRouteCalculating()) {
			updateData();
			updateVisibleType();
			updateTypesMenu();
		}
		updateInfoView();
	}

	private void updateTypesMenu() {
		if (!editingCtx.isPointsEnoughToCalculateRoute()) {
			graphTypesMenu.setVisibility(View.GONE);
		} else {
			graphTypesMenu.setVisibility(View.VISIBLE);
			graphTypesMenu.removeAllViews();
			fillInTypesMenu();
		}
	}

	private void fillInTypesMenu() {
		OsmandApplication app = getMyApplication();
		int activeColorId = nightMode ? R.color.active_color_primary_dark : R.color.active_color_primary_light;
		final HorizontalSelectionAdapter adapter = new HorizontalSelectionAdapter(app, nightMode);
		final ArrayList<HorizontalSelectionItem> items = new ArrayList<>();
		for (GraphType type : graphTypes) {
			HorizontalSelectionItem item = new HorizontalSelectionItem(type.getTitle(), type);
			if (type.isCustom()) {
				item.setTitleColorId(activeColorId);
			}
			if (type.isAvailable()) {
				items.add(item);
			}
		}
		adapter.setItems(items);
		String selectedItemKey = visibleType.getTitle();
		adapter.setSelectedItemByTitle(selectedItemKey);
		adapter.setListener(new HorizontalSelectionAdapterListener() {
			@Override
			public void onItemSelected(HorizontalSelectionItem item) {
				adapter.setItems(items);
				adapter.setSelectedItem(item);
				GraphType chosenType = (GraphType) item.getObject();
				if (!isCurrentVisibleType(chosenType)) {
					changeVisibleType(chosenType);
				}
			}
		});
		graphTypesMenu.setAdapter(adapter);
		adapter.notifyDataSetChanged();
	}

	private void changeVisibleType(GraphType type) {
		visibleType = type;
		updateInfoView();
	}

	private boolean isCurrentVisibleType(GraphType type) {
		if (visibleType != null && type != null) {
			return Algorithms.objectEquals(visibleType.getTitle(), type.getTitle());
		}
		return false;
	}

	private GraphType getFirstAvailableType() {
		for (GraphType type : graphTypes) {
			if (type.isAvailable()) {
				return type;
			}
		}
		return null;
	}

	private void updateInfoView() {
		hideAll();
		if (!editingCtx.isPointsEnoughToCalculateRoute()) {
			showMessage(app.getString(R.string.message_you_need_add_two_points_to_show_graphs));
		} else if (isRouteCalculating()) {
			showMessage(app.getString(R.string.message_graph_will_be_available_after_recalculation), true);
		} else if (visibleType.hasData()) {
			showGraph();
		} else if (visibleType.canBeCalculated()) {
			showMessage(app.getString(R.string.message_need_calculate_route_before_show_graph,
					visibleType.getTitle()), R.drawable.ic_action_altitude_average,
					app.getString(R.string.route_between_points), new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							fragment.startSnapToRoad(false);
						}
					});
		}
	}

	private void hideAll() {
		commonGraphContainer.setVisibility(View.GONE);
		customGraphContainer.setVisibility(View.GONE);
		messageContainer.setVisibility(View.GONE);
	}

	private void showMessage(String text) {
		showMessage(text, INVALID_ID, false, null, null);
	}

	private void showMessage(String text, @DrawableRes int iconResId, String btnTitle, View.OnClickListener btnListener) {
		showMessage(text, iconResId, false, btnTitle, btnListener);
	}

	private void showMessage(String text, boolean showProgressBar) {
		showMessage(text, INVALID_ID, showProgressBar, null, null);
	}

	private void showMessage(@NonNull String text,
	                         @DrawableRes int iconResId,
	                         boolean showProgressBar,
	                         String btnTitle,
	                         View.OnClickListener btnListener) {
		messageContainer.setVisibility(View.VISIBLE);
		TextView tvMessage = messageContainer.findViewById(R.id.message_text);
		tvMessage.setText(text);
		ImageView icon = messageContainer.findViewById(R.id.message_icon);
		if (iconResId != INVALID_ID) {
			icon.setVisibility(View.VISIBLE);
			icon.setImageResource(iconResId);
		} else {
			icon.setVisibility(View.GONE);
		}
		ProgressBar pb = messageContainer.findViewById(R.id.progress_bar);
		pb.setVisibility(showProgressBar ? View.VISIBLE : View.GONE);
		View btnContainer = messageContainer.findViewById(R.id.btn_container);
		if (btnTitle != null) {
			TextView tvBtnTitle = btnContainer.findViewById(R.id.btn_text);
			tvBtnTitle.setText(btnTitle);
			btnContainer.setVisibility(View.VISIBLE);
		} else {
			btnContainer.setVisibility(View.GONE);
		}
		if (btnListener != null) {
			btnContainer.setOnClickListener(btnListener);
		}
	}

	private void showGraph() {
		if (visibleType.isCustom()) {
			CustomGraphType customGraphType = (CustomGraphType) visibleType;
			customGraphContainer.setVisibility(View.VISIBLE);
			customGraphAdapter.setLegendViewType(LegendViewType.ONE_ELEMENT);
			customGraphAdapter.fullUpdate((BarData) customGraphType.getGraphData(), customGraphType.getStatistics());
		} else {
			commonGraphContainer.setVisibility(View.VISIBLE);
			customGraphAdapter.setLegendViewType(LegendViewType.GONE);
			commonGraphAdapter.fullUpdate((LineData) visibleType.getGraphData(), gpxItem);
		}
	}

	private void updateData() {
		graphTypes.clear();
		OsmandApplication app = getMyApplication();
		GPXFile gpxFile = getGpxFile();
		GPXTrackAnalysis analysis = gpxFile != null ? gpxFile.getAnalysis(0) : null;
		gpxItem = gpxFile != null ? GpxUiHelper.makeGpxDisplayItem(app, gpxFile) : null;
		if (gpxItem != null) {
			trackDetailsMenu.setGpxItem(gpxItem);
		}

		// update common graph data
		for (CommonGraphType commonType : CommonGraphType.values()) {
			List<ILineDataSet> dataSets = GpxUiHelper.getDataSets(commonGraphAdapter.getChart(),
					app, analysis, false, commonType.lineGraphTypes);
			LineData data = !Algorithms.isEmpty(dataSets) ? new LineData(dataSets) : null;
			String title = app.getString(commonType.titleId);
			graphTypes.add(new GraphType(title, commonType.canBeCalculated, data));
		}

		// update custom graph data
		List<RouteStatistics> routeStatistics = calculateRouteStatistics();
		if (analysis != null && routeStatistics != null) {
			for (RouteStatistics statistics : routeStatistics) {
				String title = AndroidUtils.getStringRouteInfoPropertyValue(app, statistics.name);
				BarData data = null;
				if (!Algorithms.isEmpty(statistics.elements)) {
					data = GpxUiHelper.buildStatisticChart(app, customGraphAdapter.getChart(),
							statistics, analysis, true, nightMode);
				}
				graphTypes.add(new CustomGraphType(title, false, data, statistics));
			}
		}
	}

	private void updateVisibleType() {
		if (visibleType == null) {
			visibleType = getFirstAvailableType();
		} else {
			for (GraphType type : graphTypes) {
				if (isCurrentVisibleType(type)) {
					visibleType = type.isAvailable() ? type : getFirstAvailableType();
					break;
				}
			}
		}
	}

	private GPXFile getGpxFile() {
		if (fragment.isTrackReadyToCalculate()) {
			return editingCtx.exportGpx(GRAPH_DATA_GPX_FILE_NAME);
		} else {
			GpxData gpxData = editingCtx.getGpxData();
			return gpxData != null ? gpxData.getGpxFile() : null;
		}
	}

	private List<RouteStatistics> calculateRouteStatistics() {
		OsmandApplication app = getMyApplication();
		List<RouteSegmentResult> route = editingCtx.getAllRouteSegments();
		if (route == null || app == null) return null;
		return RouteDetailsFragment.calculateRouteStatistics(app, route, nightMode);
	}

	private boolean isRouteCalculating() {
		return fragment.isProgressBarVisible();
	}

	private class GraphType {
		private String title;
		private boolean canBeCalculated;
		private ChartData graphData;

		public GraphType(String title, boolean canBeCalculated, ChartData graphData) {
			this.title = title;
			this.canBeCalculated = canBeCalculated;
			this.graphData = graphData;
		}

		public String getTitle() {
			return title;
		}

		public boolean isCustom() {
			return this instanceof CustomGraphType;
		}

		public boolean isAvailable() {
			return isPointsCountEnoughToCalculateRoute() && (hasData() || canBeCalculated());
		}

		private boolean isPointsCountEnoughToCalculateRoute() {
			return editingCtx.getPointsCount() >= 2;
		}

		public boolean canBeCalculated() {
			return canBeCalculated;
		}

		public boolean hasData() {
			return getGraphData() != null;
		}

		public ChartData getGraphData() {
			return graphData;
		}
	}

	private class CustomGraphType extends GraphType {

		private RouteStatistics statistics;

		public CustomGraphType(String title, boolean canBeCalculated, ChartData graphData, RouteStatistics statistics) {
			super(title, canBeCalculated, graphData);
			this.statistics = statistics;
		}

		public RouteStatistics getStatistics() {
			return statistics;
		}
	}
}
