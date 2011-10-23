package net.osmand.plus.views;

import gnu.trove.set.hash.TIntHashSet;

import java.util.ArrayList;
import java.util.List;

import net.osmand.LogUtil;
import net.osmand.OsmAndFormatter;
import net.osmand.data.Amenity;
import net.osmand.osm.LatLon;
import net.osmand.plus.PoiFilter;
import net.osmand.plus.R;
import net.osmand.plus.ResourceManager;
import net.osmand.plus.activities.EditingPOIActivity;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.render.RenderingIcons;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

public class POIMapLayer implements OsmandMapLayer, ContextMenuLayer.IContextMenuProvider {
	private static final int startZoom = 10;
	public static final int TEXT_WRAP = 15;
	public static final int TEXT_LINES = 3;
	public static final org.apache.commons.logging.Log log = LogUtil.getLog(POIMapLayer.class);
	
	
	private Paint pointAltUI;
	private Paint paintIcon;
	private Paint paintTextIcon;
	private Paint point;
	private OsmandMapTileView view;
	private List<Amenity> objects = new ArrayList<Amenity>();
	
	private ResourceManager resourceManager;
	private PoiFilter filter;
	private DisplayMetrics dm;
	private final MapActivity activity;
	
	public POIMapLayer(MapActivity activity) {
		this.activity = activity;
	}

	@Override
	public boolean onLongPressEvent(PointF point) {
		return false;
	}
	
	public PoiFilter getFilter() {
		return filter;
	}
	
	public void setFilter(PoiFilter filter) {
		this.filter = filter;
	}
	
	public Amenity getAmenityFromPoint(PointF point){
		Amenity result = null;
		if (objects != null) {
			int ex = (int) point.x;
			int ey = (int) point.y;
			int radius = getRadiusPoi(view.getZoom()) * 3 / 2;
			try {
				for (int i = 0; i < objects.size(); i++) {
					Amenity n = objects.get(i);
					int x = view.getRotatedMapXForPoint(n.getLocation().getLatitude(), n.getLocation().getLongitude());
					int y = view.getRotatedMapYForPoint(n.getLocation().getLatitude(), n.getLocation().getLongitude());
					if (Math.abs(x - ex) <= radius && Math.abs(y - ey) <= radius) {
						radius = Math.max(Math.abs(x - ex), Math.abs(y - ey));
						result = n;
					}
				}
			} catch (IndexOutOfBoundsException e) {
				// that's really rare case, but is much efficient than introduce synchronized block
			}
		}
		return result;
	}
	

	@Override
	public boolean onTouchEvent(PointF point) {
		Amenity n = getAmenityFromPoint(point);
		if(n != null){
			String format = OsmAndFormatter.getPoiSimpleFormat(n, view.getContext(),
					view.getSettings().USE_ENGLISH_NAMES.get());
			if(n.getOpeningHours() != null){
				format += "\n" + view.getContext().getString(R.string.opening_hours) +" : "+ n.getOpeningHours(); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if(n.getPhone() != null){
				format += "\n" + view.getContext().getString(R.string.phone) +" : "+ n.getPhone(); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if(n.getSite() != null){
				format += "\n" + view.getContext().getString(R.string.website) +" : "+ n.getSite(); //$NON-NLS-1$ //$NON-NLS-2$
			}
			Toast.makeText(view.getContext(), format, Toast.LENGTH_SHORT).show();
			return true;
		}
		return false;
	}
	
	
	@Override
	public void initLayer(OsmandMapTileView view) {
		this.view = view;
		dm = new DisplayMetrics();
		WindowManager wmgr = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
		wmgr.getDefaultDisplay().getMetrics(dm);

		pointAltUI = new Paint();
		pointAltUI.setColor(Color.rgb(255, 128, 0));
		pointAltUI.setAlpha(160);
		pointAltUI.setStyle(Style.FILL);
		
		paintIcon = new Paint();
		
		paintTextIcon = new Paint();
		paintTextIcon.setTextSize(12 * dm.density);
		paintTextIcon.setTextAlign(Align.CENTER);
		
		point = new Paint();
		point.setColor(Color.GRAY);
		point.setAntiAlias(true);
		point.setStyle(Style.STROKE);
		resourceManager = view.getApplication().getResourceManager();
	}
	
	public int getRadiusPoi(int zoom){
		int r = 0;
		if(zoom < startZoom){
			r = 0;
		} else if(zoom <= 15){
			r = 10;
		} else if(zoom == 16){
			r = 14;
		} else if(zoom == 17){
			r = 16;
		} else {
			r = 18;
		}
		return (int) (r * dm.density);
	}
	
	
	@Override
	public void onDraw(Canvas canvas, RectF latLonBounds, RectF tilesRect, boolean nightMode) {
		
		if (view.getZoom() >= startZoom) {
			objects.clear();
			resourceManager.searchAmenitiesAsync(latLonBounds.top, latLonBounds.left, latLonBounds.bottom, latLonBounds.right, view.getZoom(), filter, objects);
			int r = getRadiusPoi(view.getZoom());
			for (Amenity o : objects) {
				int x = view.getRotatedMapXForPoint(o.getLocation().getLatitude(), o.getLocation().getLongitude());
				int y = view.getRotatedMapYForPoint(o.getLocation().getLatitude(), o.getLocation().getLongitude());
				canvas.drawCircle(x, y, r, pointAltUI);
				canvas.drawCircle(x, y, r, point);
				String id = null;
				if(RenderingIcons.containsIcon(o.getSubType())){
					id = o.getSubType();
				} else if (RenderingIcons.containsIcon(o.getType().getDefaultTag() + "_" + o.getSubType())) {
					id = o.getType().getDefaultTag() + "_" + o.getSubType();
				}
				if(id != null){
					Bitmap bmp = RenderingIcons.getIcon(view.getContext(), id);
					if(bmp != null){
						canvas.drawBitmap(bmp, x - bmp.getWidth() / 2, y - bmp.getHeight() / 2, paintIcon);
					}
				}
			}
			
			if (view.getSettings().SHOW_POI_LABEL.get()) {
				TIntHashSet set = new TIntHashSet();
				for (Amenity o : objects) {
					int x = view.getRotatedMapXForPoint(o.getLocation().getLatitude(), o.getLocation().getLongitude());
					int y = view.getRotatedMapYForPoint(o.getLocation().getLatitude(), o.getLocation().getLongitude());
					int tx = view.getMapXForPoint(o.getLocation().getLongitude());
					int ty = view.getMapYForPoint(o.getLocation().getLatitude());
					String name = o.getName(view.getSettings().USE_ENGLISH_NAMES.get());
					if (name != null && name.length() > 0) {
						int lines = 0;
						while (lines < TEXT_LINES) {
							if (set.contains(division(tx, ty, 0, lines)) ||
									set.contains(division(tx, ty, -1, lines)) || set.contains(division(tx, ty, +1, lines))) {
								break;
							}
							lines++;
						}
						if (lines == 0) {
							// drawWrappedText(canvas, "...", paintTextIcon.getTextSize(), x, y + r + 2 + paintTextIcon.getTextSize() / 2, 1);
						} else {
							drawWrappedText(canvas, name, paintTextIcon.getTextSize(), x, y + r + 2 + paintTextIcon.getTextSize() / 2,
									lines);
							while (lines > 0) {
								set.add(division(tx, ty, 1, lines - 1));
								set.add(division(tx, ty, -1, lines - 1));
								set.add(division(tx, ty, 0, lines - 1));
								lines--;
							}
						}

					}
				}
			}

		}
	}
	
	private int division(int x, int y, int sx, int sy) {
		// make numbers positive
		return ((((x + 10000) >> 4) + sx) << 16) | (((y + 10000) >> 4) + sy);
	}
	
	private void drawWrappedText(Canvas cv, String text, float textSize, float x, float y, int lines) {
		if(text.length() > TEXT_WRAP){
			int start = 0;
			int end = text.length();
			int lastSpace = -1;
			int line = 0;
			int pos = 0;
			int limit = 0;
			while(pos < end && (line < lines)){
				lastSpace = -1;
				limit += TEXT_WRAP;
				while(pos < limit && pos < end){
					if(!Character.isLetterOrDigit(text.charAt(pos))){
						lastSpace = pos;
					}
					pos++;
				}
				if(lastSpace == -1){
					drawShadowText(cv, text.substring(start, pos), x, y + line * (textSize + 2));
					start = pos;
				} else {
					String subtext = text.substring(start, lastSpace);
					if (line + 1 == lines) {
						subtext += "..";
					}
					drawShadowText(cv, subtext, x, y + line * (textSize + 2));
					
					start = lastSpace + 1;
					limit += (start - pos) - 1;
				}
				
				line++;
				
				
			}
		} else {
			drawShadowText(cv, text, x, y);
		}
	}
	
	private void drawShadowText(Canvas cv, String text, float centerX, float centerY) {
		int c = paintTextIcon.getColor();
		paintTextIcon.setStyle(Style.STROKE);
		paintTextIcon.setColor(Color.WHITE);
		paintTextIcon.setStrokeWidth(2);
		cv.drawText(text, centerX, centerY, paintTextIcon);
		// reset
		paintTextIcon.setStrokeWidth(2);
		paintTextIcon.setStyle(Style.FILL);
		paintTextIcon.setColor(c);
		cv.drawText(text, centerX, centerY, paintTextIcon);
	}

	@Override
	public void destroyLayer() {
	}

	@Override
	public boolean drawInScreenPixels() {
		return true;
	}

	@Override
	public OnClickListener getActionListener(List<String> actionsList, Object o) {
		final Amenity a = (Amenity) o;
		actionsList.add(this.view.getResources().getString(R.string.poi_context_menu_modify));
		actionsList.add(this.view.getResources().getString(R.string.poi_context_menu_delete));
		int ind = 2;
		final int phoneIndex = a.getPhone() != null ? ind++ : -1;
		final int siteIndex = a.getSite() != null ? ind++ : -1;
		if(a.getPhone() != null){
			actionsList.add(this.view.getResources().getString(R.string.poi_context_menu_call));
		}
		if(a.getSite() != null){
			actionsList.add(this.view.getResources().getString(R.string.poi_context_menu_website));
		}
		final EditingPOIActivity edit = activity.getPoiActions();
		return new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (which == 0) {
					edit.showEditDialog(a);
				} else if(which == 1) {
					edit.showDeleteDialog(a);
				} else if (which == phoneIndex) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(Uri.parse("tel:"+a.getPhone())); //$NON-NLS-1$
						view.getContext().startActivity(intent);
					} catch (RuntimeException e) {
						log.error("Failed to invoke call", e); //$NON-NLS-1$
						Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
					}
				} else if (which == siteIndex) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(Uri.parse(a.getSite())); 
						view.getContext().startActivity(intent);
					} catch (RuntimeException e) {
						log.error("Failed to invoke call", e); //$NON-NLS-1$
						Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
					}
				} else {
				}
			}
		};
	}

	@Override
	public String getObjectDescription(Object o) {
		if(o instanceof Amenity){
			return OsmAndFormatter.getPoiSimpleFormat((Amenity) o, view.getContext(), view.getSettings().USE_ENGLISH_NAMES.get());
		}
		return null;
	}

	@Override
	public Object getPointObject(PointF point) {
		return getAmenityFromPoint(point);
	}

	@Override
	public LatLon getObjectLocation(Object o) {
		if(o instanceof Amenity){
			return ((Amenity)o).getLocation();
		}
		return null;
	}

}
