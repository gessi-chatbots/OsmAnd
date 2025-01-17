package net.osmand.plus.views.mapwidgets.configure.reorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.views.controls.ReorderItemTouchHelperCallback.OnItemMoveCallback;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.ActionButtonViewHolder;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.ActionButtonViewHolder.ActionButtonInfo;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.AddPageButtonViewHolder;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.AvailableWidgetViewHolder;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.AvailableWidgetViewHolder.AvailableWidgetUiInfo;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.DividerViewHolder;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.HeaderViewHolder;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.PageViewHolder;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.PageViewHolder.PageUiInfo;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.SpaceViewHolder;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.AddedWidgetViewHolder;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.AddedWidgetViewHolder.ItemMovableCallback;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.AddedWidgetViewHolder.AddedWidgetUiInfo;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import static net.osmand.plus.utils.UiUtilities.getColoredSelectableDrawable;

public class ReorderWidgetsAdapter extends Adapter<ViewHolder> implements OnItemMoveCallback, ItemMovableCallback {

	private final OsmandApplication app;
	private final ReorderWidgetsAdapterHelper reorderHelper;
	private final List<ListItem> items = new ArrayList<>();

	private WidgetAdapterListener listener;
	private final boolean nightMode;
	private final int profileColor;
	private final int defaultIconColor;

	public enum ItemType {
		HEADER,
		PAGE,
		ADDED_WIDGET,
		AVAILABLE_WIDGET,
		ADD_PAGE_BUTTON,
		CARD_DIVIDER,
		CARD_TOP_DIVIDER,
		CARD_BOTTOM_DIVIDER,
		SPACE,
		ACTION_BUTTON
	}

	public ReorderWidgetsAdapter(@NonNull OsmandApplication app, @NonNull WidgetsDataHolder dataHolder, boolean nightMode) {
		setHasStableIds(true);
		this.app = app;
		this.nightMode = nightMode;
		this.reorderHelper = new ReorderWidgetsAdapterHelper(this, dataHolder, items);

		ApplicationMode mode = app.getSettings().getApplicationMode();
		profileColor = mode.getProfileColor(nightMode);
		defaultIconColor = ColorUtilities.getDefaultIconColor(app, nightMode);
	}

	@NonNull
	public List<ListItem> getItems() {
		return items;
	}

	public void setItems(@NonNull List<ListItem> items) {
		this.items.clear();
		this.items.addAll(items);
		notifyDataSetChanged();
	}

	public void setListener(@NonNull WidgetAdapterListener listener) {
		this.listener = listener;
	}

	public void restorePage(int page, int position) {
		reorderHelper.restorePage(page, position);
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		Context ctx = parent.getContext();
		LayoutInflater inflater = UiUtilities.getInflater(ctx, nightMode);

		switch (ItemType.values()[viewType]) {
			case HEADER:
				return new HeaderViewHolder(inflater.inflate(R.layout.configure_screen_list_item_header, parent, false));
			case PAGE:
				View itemView = inflater.inflate(R.layout.configure_screen_list_item_page_reorder, parent, false);
				return new PageViewHolder(itemView);
			case ADDED_WIDGET:
				itemView = inflater.inflate(R.layout.configure_screen_list_item_widget_reorder, parent, false);
				return new AddedWidgetViewHolder(itemView, ReorderWidgetsAdapter.this);
			case ADD_PAGE_BUTTON:
				itemView = inflater.inflate(R.layout.configure_screen_list_item_add_page, parent, false);
				return new AddPageButtonViewHolder(itemView, profileColor);
			case AVAILABLE_WIDGET:
				itemView = inflater.inflate(R.layout.configure_screen_list_item_available_widget_reorder, parent, false);
				return new AvailableWidgetViewHolder(itemView);
			case CARD_DIVIDER:
				return new DividerViewHolder(inflater.inflate(R.layout.list_item_divider, parent, false));
			case CARD_TOP_DIVIDER:
				itemView = inflater.inflate(R.layout.list_item_divider, parent, false);
				View bottomShadow = itemView.findViewById(R.id.bottomShadowView);
				bottomShadow.setVisibility(View.INVISIBLE);
				return new DividerViewHolder(itemView);
			case CARD_BOTTOM_DIVIDER:
				return new DividerViewHolder(inflater.inflate(R.layout.card_bottom_divider, parent, false));
			case SPACE:
				return new SpaceViewHolder(new View(ctx));
			case ACTION_BUTTON:
				return new ActionButtonViewHolder(inflater.inflate(R.layout.configure_screen_list_item_button, parent, false));
			default:
				throw new IllegalArgumentException("Unsupported view type");
		}
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
		ListItem item = items.get(position);
		if (viewHolder instanceof PageViewHolder) {
			bindPageViewHolder(((PageViewHolder) viewHolder), position);
		} else if (viewHolder instanceof AddedWidgetViewHolder) {
			bindAddedWidgetViewHolder(((AddedWidgetViewHolder) viewHolder), position);
		} else if (viewHolder instanceof AvailableWidgetViewHolder) {
			bindAvailableWidgetViewHolder(((AvailableWidgetViewHolder) viewHolder), position);
		} else if (viewHolder instanceof HeaderViewHolder) {
			HeaderViewHolder holder = (HeaderViewHolder) viewHolder;
			holder.title.setText((String) item.value);
		} else if (viewHolder instanceof ActionButtonViewHolder) {
			ActionButtonInfo actionButtonInfo = (ActionButtonInfo) item.value;

			ActionButtonViewHolder holder = (ActionButtonViewHolder) viewHolder;
			holder.buttonView.setOnClickListener(actionButtonInfo.listener);
			holder.icon.setImageResource(actionButtonInfo.iconRes);
			holder.title.setText(actionButtonInfo.title);

			AndroidUtils.setBackground(holder.buttonView, getColoredSelectableDrawable(app, profileColor, 0.3f));
		} else if (viewHolder instanceof SpaceViewHolder) {
			SpaceViewHolder holder = (SpaceViewHolder) viewHolder;
			holder.setHeight((int) item.value);
		} else if (viewHolder instanceof AddPageButtonViewHolder) {
			((AddPageButtonViewHolder) viewHolder).buttonContainer.setOnClickListener(v -> reorderHelper.addPage());
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	private void bindPageViewHolder(@NonNull PageViewHolder viewHolder, int position) {
		int pageIndex = ((PageUiInfo) items.get(position).value).index;

		OnClickListener deletePageListener;
		Drawable deleteIcon;
		boolean firstPage = pageIndex == 0;
		if (firstPage) {
			deletePageListener = null;
			deleteIcon = getDeleteIcon(true);
		} else {
			deletePageListener = v -> deletePage(viewHolder.getAdapterPosition(), pageIndex);
			deleteIcon = getDeleteIcon(false);
		}
		viewHolder.deletePageButton.setOnClickListener(deletePageListener);
		viewHolder.deletePageButton.setImageDrawable(deleteIcon);

		AndroidUiHelper.updateVisibility(viewHolder.topDivider, !firstPage);
		viewHolder.pageText.setText(app.getString(R.string.page_number, String.valueOf(pageIndex + 1)));

		AndroidUiHelper.updateVisibility(viewHolder.moveIcon, !firstPage);
		viewHolder.moveIcon.setOnTouchListener((v, event) -> {
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
				listener.onDragStarted(viewHolder);
			}
			return false;
		});
	}

	private void deletePage(int position, int pageToDelete) {
		reorderHelper.deletePage(position, pageToDelete);
		if (listener != null) {
			listener.onPageDeleted(pageToDelete, position);
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	private void bindAddedWidgetViewHolder(@NonNull AddedWidgetViewHolder viewHolder, int position) {
		AddedWidgetUiInfo widgetInfo = (AddedWidgetUiInfo) items.get(position).value;

		viewHolder.deleteWidgetButton.setImageDrawable(getDeleteIcon(false));
		viewHolder.deleteWidgetButton.setOnClickListener(v -> {
			int pos = viewHolder.getAdapterPosition();
			reorderHelper.deleteWidget(widgetInfo, pos);
		});

		viewHolder.title.setText(widgetInfo.title);
		viewHolder.moveIcon.setOnTouchListener((view, event) -> {
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
				listener.onDragStarted(viewHolder);
			}
			return false;
		});
		viewHolder.icon.setImageResource(widgetInfo.iconId);
		AddedWidgetViewHolder.updateWidgetIcon(viewHolder.icon, widgetInfo.info, profileColor, defaultIconColor, true, nightMode);
	}

	private void bindAvailableWidgetViewHolder(@NonNull AvailableWidgetViewHolder viewHolder, int position) {
		AvailableWidgetUiInfo widgetInfo = (AvailableWidgetUiInfo) items.get(position).value;

		Drawable addWidgetIcon = app.getUIUtilities().getIcon(R.drawable.ic_action_add, R.color.color_osm_edit_create);
		viewHolder.addWidgetButton.setImageDrawable(addWidgetIcon);
		viewHolder.addWidgetButton.setOnClickListener(v -> {
			int pos = viewHolder.getAdapterPosition();
			reorderHelper.addWidget(pos);
		});

		viewHolder.icon.setImageResource(widgetInfo.iconId);
		AddedWidgetViewHolder.updateWidgetIcon(viewHolder.icon, widgetInfo.info, profileColor, defaultIconColor, false, nightMode);
		viewHolder.title.setText(widgetInfo.title);

		boolean isLast = position + 1 == items.size() || items.get(position + 1).type != ItemType.AVAILABLE_WIDGET;
		AndroidUiHelper.updateVisibility(viewHolder.bottomDivider, !isLast);
	}

	@Override
	public boolean onItemMove(int from, int to) {
		return reorderHelper.swapItemsIfAllowed(from, to);
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	@Override
	public int getItemViewType(int position) {
		ListItem item = items.get(position);
		return item.type.ordinal();
	}

	@Override
	public void onItemDismiss(ViewHolder holder) {
		listener.onDragOrSwipeEnded(holder);
	}

	@Override
	public long getItemId(int position) {
		ListItem item = items.get(position);
		if (item.value instanceof AddedWidgetUiInfo) {
			return ((AddedWidgetUiInfo) item.value).key.hashCode();
		} else if (item.value instanceof ActionButtonInfo) {
			return ((ActionButtonInfo) item.value).title.hashCode();
		} else if (item.value != null) {
			return item.value.hashCode();
		}
		return item.hashCode();
	}

	@Override
	public boolean isListItemMovable(int position) {
		Object itemValue = items.get(position).value;
		return itemValue instanceof PageUiInfo || itemValue instanceof AddedWidgetUiInfo;
	}

	@NonNull
	private Drawable getDeleteIcon(boolean disabled) {
		return disabled
				? app.getUIUtilities().getIcon(R.drawable.ic_action_remove, nightMode)
				: app.getUIUtilities().getIcon(R.drawable.ic_action_remove, R.color.color_osm_edit_delete);
	}

	public static class ListItem {

		public final ItemType type;
		public final Object value;

		public ListItem(@NonNull ItemType type, @Nullable Object value) {
			this.type = type;
			this.value = value;
		}
	}

	public interface WidgetAdapterListener {

		void onDragStarted(ViewHolder holder);

		void onDragOrSwipeEnded(ViewHolder holder);

		void onPageDeleted(int page, int position);
	}
}