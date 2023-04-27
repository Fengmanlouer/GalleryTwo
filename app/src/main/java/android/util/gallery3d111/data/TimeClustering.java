/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.util.gallery3d111.data;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import android.util.Log;
import android.util.gallery3d111.common.Utils;
import android.util.gallery3d111.util.GalleryUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class TimeClustering extends Clustering {
    @SuppressWarnings("unused")
    private static final String TAG = "TimeClustering";

    // If 2 items are greater than 25 miles apart, they will be in different clusters.
    // 如果两个项目的距离大于25英里，它们将处于不同的相册集中。（地理位置截至的距离（英里）=20）
    private static final int GEOGRAPHIC_DISTANCE_CUTOFF_IN_MILES = 20;

    // Do not want to split based on anything under 1 min.
    // 不希望基于1分钟以下的任何内容进行拆分。 （最小相册集拆分时间（毫秒）=60000（一分钟））
    private static final long MIN_CLUSTER_SPLIT_TIME_IN_MS = 60000L;

    // Disregard a cluster split time of anything over 2 hours.
    // 忽略任何超过2小时的相册集拆分时间。 （最大相册集拆分时间（毫秒）=60000（两小时））
    private static final long MAX_CLUSTER_SPLIT_TIME_IN_MS = 7200000L;

    // Try and get around 9 clusters (best-effort for the common case).
    // 试着克服9个相册集（针对常见情况尽最大努力）。  （目标相册集数量=9）
    private static final int NUM_CLUSTERS_TARGETED = 9;

    // Try and merge 2 clusters if they are both smaller than min cluster size.
    // The min cluster size can range from 8 to 15.
    // 如果两个相册集都小于最小相册集大小，尝试合并两个相册集。最小相册集大小在8到15之间。
    private static final int MIN_MIN_CLUSTER_SIZE = 8;
    private static final int MAX_MIN_CLUSTER_SIZE = 15;

    // Try and split a cluster if it is bigger than max cluster size.
    // The max cluster size can range from 20 to 50.
    // 如果相册集大于最大相册集大小，请尝试拆分相册集。最大相册集大小在20到50之间。
    private static final int MIN_MAX_CLUSTER_SIZE = 20;
    private static final int MAX_MAX_CLUSTER_SIZE = 50;

    // Initially put 2 items in the same cluster as long as they are within
    // 3 cluster frequencies of each other.
    // 最初将2个项目放在同一相册集中，只要它们在彼此的3个相册集频率内。 （相册集拆分乘数？？？）
    private static int CLUSTER_SPLIT_MULTIPLIER = 3;

    // The minimum change factor in the time between items to consider a partition.
    // 考虑分区的项目之间时间的最小变化因子。 （最小分区变化因子=2）
    // Example: (Item 3 - Item 2) / (Item 2 - Item 1).
    private static final int MIN_PARTITION_CHANGE_FACTOR = 2;

    // Make the cluster split time of a large cluster half that of a regular cluster.
    // 使大型相册集的相册集拆分时间为常规相册集的一半。 （相册集拆分时间因子 = 2）
    private static final int PARTITION_CLUSTER_SPLIT_TIME_FACTOR = 2;

    private Context mContext;
    private ArrayList<Cluster> mClusters; //数组列表中存储Cluster类型数据
    private String[] mNames;   //String类型数组
    private Cluster mCurrCluster;

    //m相册拆分时间 = （1分钟+2小时）/2
    private long mClusterSplitTime = (MIN_CLUSTER_SPLIT_TIME_IN_MS + MAX_CLUSTER_SPLIT_TIME_IN_MS) / 2;

    //m大相册集拆分时间 = m相册拆分时间/2
    private long mLargeClusterSplitTime = mClusterSplitTime / PARTITION_CLUSTER_SPLIT_TIME_FACTOR;

    //m最小相册集大小 = （8+15）/2
    private int mMinClusterSize = (MIN_MIN_CLUSTER_SIZE + MAX_MIN_CLUSTER_SIZE) / 2;

    //m最大相册集大小 = （20+50）/2
    private int mMaxClusterSize = (MIN_MAX_CLUSTER_SIZE + MAX_MAX_CLUSTER_SIZE) / 2;

    private static final Comparator<SmallItem> sDateComparator = new DateComparator();

    //实现Comparator接口，<>中放比较的类。通过创建新的一个类实现比较的功能。可以写多个类，比较不同属性时，运用相应的类。
    //在return后进行功能的实现 （比较日期）
    private static class DateComparator implements Comparator<SmallItem> {
        @Override
        public int compare(SmallItem item1, SmallItem item2) {
            return -Utils.compare(item1.dateInMs, item2.dateInMs);
        }
    }

    //构造方法
    public TimeClustering(Context context) {
        mContext = context;
        mClusters = new ArrayList<Cluster>();
        mCurrCluster = new Cluster();
    }

    @Override
    public void run(MediaSet baseSet) {
        final int total = baseSet.getTotalMediaItemCount();  //所有媒体项目数量
        final SmallItem[] buf = new SmallItem[total];   //buf 缓冲器
        final double[] latLng = new double[2];  //经纬度


        //枚举媒体项目总数并将其添加到ArrayList<SmallItem> items中
        baseSet.enumerateTotalMediaItems(new MediaSet.ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                if (index < 0 || index >= total) return;
                SmallItem s = new SmallItem();
                s.path = item.getPath();
                s.dateInMs = item.getDateInMs();
                item.getLatLong(latLng);
                s.lat = latLng[0];
                s.lng = latLng[1];
                buf[index] = s;
            }
        });

        ArrayList<SmallItem> items = new ArrayList<SmallItem>(total);
        for (int i = 0; i < total; i++) {
            if (buf[i] != null) {
                items.add(buf[i]);
            }
        }

        //collections是一个集合操作工具类，主要实现对集合的操作，其中sort方法是对list集合元素进行排序
        //根据日期对items进行排序
        Collections.sort(items, sDateComparator);


        if(false){
            //得到所有items的最早和最晚时间
            int n = items.size();
            long minTime = 0;
            long maxTime = 0;
            for (int i = 0; i < n; i++) {
                long t = items.get(i).dateInMs;
                if (t == 0) continue;
                if (minTime == 0) {
                    minTime = maxTime = t;
                } else {
                    minTime = Math.min(minTime, t);
                    maxTime = Math.max(maxTime, t);
                }
            }
            //设置时间范围
            setTimeRange(maxTime - minTime, n);
            //分类相册集
            for (int i = 0; i < n; i++) {
                compute(items.get(i));
            }
            compute(null);
        }
        else {
            computeNew(items);
        }



        //生成相册集名称（日期说明）
        int m = mClusters.size();
        mNames = new String[m];
        for (int i = 0; i < m; i++) {
            mNames[i] = mClusters.get(i).generateCaption(mContext);
        }
    }

    private static final String MMDDYY_FORMAT1 = "MMddyy";  //日期格式
    private void computeNew(ArrayList<SmallItem> items) {
        int n = items.size();   //所有项目总数
        Log.d(TAG, "computeNew: 00000000000000000000000000000000"+n);
        if (n<1){return;}
        String strDataLast = "";  //最后的日期数据
        for (int i = 0; i < n; i++) {
            SmallItem currentItem = items.get(i);  //获取当前项目的索引
            long t = currentItem.dateInMs;   //当前项目的日期（以毫秒ms为单位）
            if (t==0) {continue;}
            //strDate即当前项目的日期，将他用毫秒ms的形式表示
            //String strDate = String.valueOf(t).substring(0,6);
            String strDate = DateFormat.format(MMDDYY_FORMAT1, t).toString();  //格式化日期时间
            //如果最后的时间数据为空，或 最后的时间数据与strDate相同，将项目添加到当前相册集
            //equalsIgnoreCase() 方法用于将字符串与指定的对象比较，不考虑大小写。equals() 会判断大小写区别
            if (strDataLast.isEmpty()||strDataLast.equalsIgnoreCase(strDate)){
                mCurrCluster.addItem(currentItem);
                //Log.d(TAG, "computeNew: 111111111+"+i+"+"+currentItem.dateInMs);
            }
            //否则，新建一个相册集并将当前项目添加进去
            else {
                mClusters.add(mCurrCluster);
                mCurrCluster = new Cluster();
                //Log.d(TAG, "computeNew: 222222222+"+i+"+"+currentItem.dateInMs);
                mCurrCluster.addItem(currentItem);

            }
            //将当前项目的日期设置为最新的需要对比的日期
            strDataLast = strDate;
        }
        mClusters.add(mCurrCluster);
    }


    //获取相册集数量
    @Override
    public int getNumberOfClusters() {
        return mClusters.size();
    }

    @Override
    public ArrayList<Path> getCluster(int index) {
        ArrayList<SmallItem> items = mClusters.get(index).getItems();
        ArrayList<Path> result = new ArrayList<Path>(items.size());
        for (int i = 0, n = items.size(); i < n; i++) {
            result.add(items.get(i).path);
        }
        return result;
    }

    @Override
    public String getClusterName(int index) {
        return mNames[index];
    }

    private void setTimeRange(long timeRange, int numItems) {
        if (numItems != 0) {
            int meanItemsPerCluster = numItems / NUM_CLUSTERS_TARGETED;  //每个相册集的平均项目数=总数/9
            // Heuristic to get min and max cluster size - half and double the desired items per cluster.
            // 最小相册集大小
            mMinClusterSize = meanItemsPerCluster / 2;
            //最大相册集大小
            mMaxClusterSize = meanItemsPerCluster * 2;
            //m相册拆分时间
            mClusterSplitTime = timeRange / numItems * CLUSTER_SPLIT_MULTIPLIER;  //CLUSTER_SPLIT_MULTIPLIER = 3
        }
        //clamp(minimum, maximum, valueToClamp)
        //给定一个值，如果这个值小于最小值，则返回最小值，如果大于最大值，则返回最大值，如果在最小值和最大值之间，则返回原本给定的值。
        mClusterSplitTime = Utils.clamp(mClusterSplitTime, MIN_CLUSTER_SPLIT_TIME_IN_MS, MAX_CLUSTER_SPLIT_TIME_IN_MS); //（一分钟，两小时）
        mLargeClusterSplitTime = mClusterSplitTime / PARTITION_CLUSTER_SPLIT_TIME_FACTOR;  //PARTITION_CLUSTER_SPLIT_TIME_FACTOR=2
        mMinClusterSize = Utils.clamp(mMinClusterSize, MIN_MIN_CLUSTER_SIZE, MAX_MIN_CLUSTER_SIZE); //（8，15）
        mMaxClusterSize = Utils.clamp(mMaxClusterSize, MIN_MAX_CLUSTER_SIZE, MAX_MAX_CLUSTER_SIZE); //（20，50）
    }


    private void compute(SmallItem currentItem) {
        if (currentItem != null) {
            int numClusters = mClusters.size();   //相册集数
            int numCurrClusterItems = mCurrCluster.size();  //当前相册集的项目数
            boolean geographicallySeparateItem = false;  //按地理位置分隔项目
            boolean itemAddedToCurrentCluster = false;  //添加到当前相册集的项目

            // Determine if this item should go in the current cluster or be the start of a new cluster.
            // 确定此项目是应该进入当前相册集还是作为新相册集的开始。
            if (numCurrClusterItems == 0) {
                mCurrCluster.addItem(currentItem);
            } else {
                SmallItem prevItem = mCurrCluster.getLastItem();
                //如果a、b在地理位置上足够分离，则返回true
                if (isGeographicallySeparated(prevItem, currentItem)) {
                    //增加一个新的相册集
                    mClusters.add(mCurrCluster);
                    geographicallySeparateItem = true;
                    // 如果当前相册集的项目数>最大相册集的大小
                } else if (numCurrClusterItems > mMaxClusterSize) {
                    //拆分并添加当前相册集
                    splitAndAddCurrentCluster();
                    // 如果当前项目和上一个项目之间的时间间隔<相册拆分时间
                } else if (timeDistance(prevItem, currentItem) < mClusterSplitTime) {
                    //将该项目添加到当前相册集
                    mCurrCluster.addItem(currentItem);
                    itemAddedToCurrentCluster = true;
                    //如果相册集数量>0，当前相册集的项目数<最小相册集的大小，当前相册集在地理位置上不与上一个分离
                } else if (numClusters > 0 && numCurrClusterItems < mMinClusterSize
                        && !mCurrCluster.mGeographicallySeparatedFromPrevCluster) {
                    mergeAndAddCurrentCluster();   //合并并添加当前相册集
                } else {
                    //增加一个新的相册集
                    mClusters.add(mCurrCluster);
                }

                // Creating a new cluster and adding the current item to it.
                // 创建一个新的集群并将当前项目添加到其中。
                if (!itemAddedToCurrentCluster) {
                    mCurrCluster = new Cluster();
                    if (geographicallySeparateItem) {
                        mCurrCluster.mGeographicallySeparatedFromPrevCluster = true;
                    }
                    mCurrCluster.addItem(currentItem);
                }
            }
        } else {
            if (mCurrCluster.size() > 0) {
                int numClusters = mClusters.size();
                int numCurrClusterItems = mCurrCluster.size();

                // The last cluster may potentially be too big or too small.
                // 最后一个集群可能太大或太小。
                if (numCurrClusterItems > mMaxClusterSize) {
                    splitAndAddCurrentCluster();  //拆分并添加当前相册集
                } else if (numClusters > 0 && numCurrClusterItems < mMinClusterSize
                        && !mCurrCluster.mGeographicallySeparatedFromPrevCluster) {
                    mergeAndAddCurrentCluster();  //合并并添加当前相册集
                } else {
                    mClusters.add(mCurrCluster);
                }
                mCurrCluster = new Cluster();
            }
        }
    }

    //拆分并添加当前相册集
    private void splitAndAddCurrentCluster() {
        ArrayList<SmallItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.size();
        int secondPartitionStartIndex = getPartitionIndexForCurrentCluster();
        if (secondPartitionStartIndex != -1) {
            Cluster partitionedCluster = new Cluster();
            for (int j = 0; j < secondPartitionStartIndex; j++) {
                partitionedCluster.addItem(currClusterItems.get(j));
            }
            mClusters.add(partitionedCluster);
            partitionedCluster = new Cluster();
            for (int j = secondPartitionStartIndex; j < numCurrClusterItems; j++) {
                partitionedCluster.addItem(currClusterItems.get(j));
            }
            mClusters.add(partitionedCluster);
        } else {
            mClusters.add(mCurrCluster);
        }
    }

    //获取当前相册集的分区索引
    private int getPartitionIndexForCurrentCluster() {
        int partitionIndex = -1;
        float largestChange = MIN_PARTITION_CHANGE_FACTOR;
        ArrayList<SmallItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.size();
        int minClusterSize = mMinClusterSize;

        // Could be slightly more efficient here but this code seems cleaner.
        // 这里可能会稍微高效一些，但这段代码看起来更干净。
        if (numCurrClusterItems > minClusterSize + 1) {
            for (int i = minClusterSize; i < numCurrClusterItems - minClusterSize; i++) {
                SmallItem prevItem = currClusterItems.get(i - 1);
                SmallItem currItem = currClusterItems.get(i);
                SmallItem nextItem = currClusterItems.get(i + 1);

                long timeNext = nextItem.dateInMs;
                long timeCurr = currItem.dateInMs;
                long timePrev = prevItem.dateInMs;

                if (timeNext == 0 || timeCurr == 0 || timePrev == 0) continue;

                long diff1 = Math.abs(timeNext - timeCurr);
                long diff2 = Math.abs(timeCurr - timePrev);

                float change = Math.max(diff1 / (diff2 + 0.01f), diff2 / (diff1 + 0.01f));
                if (change > largestChange) {
                    if (timeDistance(currItem, prevItem) > mLargeClusterSplitTime) {
                        partitionIndex = i;
                        largestChange = change;
                    } else if (timeDistance(nextItem, currItem) > mLargeClusterSplitTime) {
                        partitionIndex = i + 1;
                        largestChange = change;
                    }
                }
            }
        }
        return partitionIndex;
    }

    //合并并添加当前相册集
    private void mergeAndAddCurrentCluster() {
        int numClusters = mClusters.size();
        Cluster prevCluster = mClusters.get(numClusters - 1);
        ArrayList<SmallItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.size();
        if (prevCluster.size() < mMinClusterSize) {
            for (int i = 0; i < numCurrClusterItems; i++) {
                prevCluster.addItem(currClusterItems.get(i));
            }
            mClusters.set(numClusters - 1, prevCluster);
        } else {
            mClusters.add(mCurrCluster);
        }
    }

    // Returns true if a, b are sufficiently geographically separated.
    // 如果a、b在地理位置上足够分离，则返回true。
    private static boolean isGeographicallySeparated(SmallItem itemA, SmallItem itemB) {
        //如果不是有效位置，则返回false
        if (!GalleryUtils.isValidLocation(itemA.lat, itemA.lng)
                || !GalleryUtils.isValidLocation(itemB.lat, itemB.lng)) {
            return false;
        }

        double distance = GalleryUtils.fastDistanceMeters(
                Math.toRadians(itemA.lat),
                Math.toRadians(itemA.lng),
                Math.toRadians(itemB.lat),
                Math.toRadians(itemB.lng));
        return (GalleryUtils.toMile(distance) > GEOGRAPHIC_DISTANCE_CUTOFF_IN_MILES); //GEOGRAPHIC_DISTANCE_CUTOFF_IN_MILES=20
    }

    // Returns the time interval between the two items in milliseconds.
    // 返回两个项目之间的时间间隔（以毫秒为单位）。
    private static long timeDistance(SmallItem a, SmallItem b) {
        return Math.abs(a.dateInMs - b.dateInMs);
    }
}

class SmallItem {
    Path path;  //路径
    long dateInMs;  //日期（以毫秒为单位）
    double lat, lng;  //Latitude纬度，Longitude经度
}

class Cluster {
    @SuppressWarnings("unused")
    private static final String TAG = "Cluster";
    private static final String MMDDYY_FORMAT = "MMddyy";  //日期格式

    // This is for TimeClustering only. 这只用于时间聚类
    public boolean mGeographicallySeparatedFromPrevCluster = false;  //在地理位置上与上一个相册集分离--否

    private ArrayList<SmallItem> mItems = new ArrayList<SmallItem>();  //创建ArrayList集合

    public Cluster() {}

    // 向集合中添加元素
    public void addItem(SmallItem item) {
        mItems.add(item);
    }

    //返回集合的长度
    public int size() {
        return mItems.size();
    }

    //获取最后一个item
    public SmallItem getLastItem() {
        int n = mItems.size();
        return (n == 0) ? null : mItems.get(n - 1);
    }

    public ArrayList<SmallItem> getItems() {
        return mItems;
    }

    //生成日期说明
    public String generateCaption(Context context) {
        int n = mItems.size();  //相册集大小
        long minTimestamp = 0;  //最小时间戳
        long maxTimestamp = 0;  //最大时间戳

        //得到相册集的时间范围
        for (int i = 0; i < n; i++) {
            long t = mItems.get(i).dateInMs;  //相册集中第i个item的日期
            if (t == 0) continue;    //continue语句用于循环语句中，作用是不执行循环体剩余部分，直接进行下次循环
            if (minTimestamp == 0) {
                minTimestamp = maxTimestamp = t;
            } else {
                minTimestamp = Math.min(minTimestamp, t);
                maxTimestamp = Math.max(maxTimestamp, t);
            }
        }
        if (minTimestamp == 0) return "";

        String caption;   //caption说明文字
        String minDay = DateFormat.format(MMDDYY_FORMAT, minTimestamp).toString();  //格式化日期时间
        String maxDay = DateFormat.format(MMDDYY_FORMAT, maxTimestamp).toString();

        //截取字符串对比年份mmddyy
        if (minDay.substring(4).equals(maxDay.substring(4))) {
            // The items are from the same year - show at least as much granularity as abbrev_all allows.
            // 这些项目来自同一年——显示的粒度至少与abbrev_all允许的粒度一样多。
            // 获取时间差
            caption = DateUtils.formatDateRange(context, minTimestamp, maxTimestamp, DateUtils.FORMAT_ABBREV_ALL);

            // Get a more granular date range string if the min and max timestamp are on the same day and from the current year.
            //如果最小时间戳和最大时间戳在同一天并且来自当前年份，则获取更精细的日期范围字符串。
            if (minDay.equals(maxDay)) {
                int flags = DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE;
                // Contains the year only if the date does not correspond to the current year.
                // 仅当日期与当前年份不对应时才包含年份。
                String dateRangeWithOptionalYear = DateUtils.formatDateTime(context, minTimestamp, flags);
                String dateRangeWithYear = DateUtils.formatDateTime(context, minTimestamp, flags | DateUtils.FORMAT_SHOW_YEAR);
                if (!dateRangeWithOptionalYear.equals(dateRangeWithYear)) {
                    // This means both dates are from the same year 这意味着两个日期都来自同一年
                    // - show the time. 显示时间
                    // Not enough room to display the time range. 没有足够的空间显示时间范围
                    // Pick the mid-point. 选择中点
                    long midTimestamp = (minTimestamp + maxTimestamp) / 2;
                    caption = DateUtils.formatDateRange(context, midTimestamp,
                            midTimestamp, DateUtils.FORMAT_SHOW_TIME | flags);
                }
            }
        } else {
            // The items are not from the same year - only show month and year.
            // 这些项目不是同一年的，只显示月份和年份。
            int flags = DateUtils.FORMAT_NO_MONTH_DAY | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE;
            caption = DateUtils.formatDateRange(context, minTimestamp, maxTimestamp, flags);
        }

        return caption;
    }
}
