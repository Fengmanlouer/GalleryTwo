/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.util.gallery3d111.filtershow.filters;

import android.util.gallery3d111.filtershow.pipeline.ImagePreset;
import android.util.gallery3d111.filtershow.editors.ImageOnlyEditor;

public class FilterUserPresetRepresentation extends FilterRepresentation {

    private ImagePreset mPreset;
    private int mId;

    public FilterUserPresetRepresentation(String name, ImagePreset preset, int id) {
        super(name);
        setEditorId(ImageOnlyEditor.ID);
        setFilterType(TYPE_FX);
        setSupportsPartialRendering(true);
        mPreset = preset;
        mId = id;
    }

    public ImagePreset getImagePreset() {
        return mPreset;
    }

    public int getId() {
        return mId;
    }

    public FilterRepresentation copy(){
        FilterRepresentation representation = new FilterUserPresetRepresentation(getName(),
                new ImagePreset(mPreset), mId);
        return representation;
    }

    @Override
    public boolean allowsSingleInstanceOnly() {
        return true;
    }
}
