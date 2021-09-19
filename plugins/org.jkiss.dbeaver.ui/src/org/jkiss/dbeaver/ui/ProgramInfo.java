/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2017 Serge Rider (serge@jkiss.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.program.Program;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.utils.CommonUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Program info
 */
public class ProgramInfo {
    private static final Map<String, ProgramInfo> programMap = new HashMap<>();

    private final Program program;
    private DBIconBinary image;

    public ProgramInfo(Program program) {
        this.program = program;
    }

    public Program getProgram() {
        return program;
    }

    public DBPImage getImage() {
        return image;
    }


    public static ProgramInfo getProgram(IResource resource)
    {
        if (resource instanceof IFile) {
            final String fileExtension = CommonUtils.notEmpty(resource.getFileExtension());
            ProgramInfo programInfo = programMap.get(fileExtension);
            if (programInfo == null) {
                Program program = Program.findProgram(fileExtension);
                programInfo = new ProgramInfo(program);
                if (program != null) {
                    final ImageData imageData = program.getImageData();
                    if (imageData != null) {
                        programInfo.image = new DBIconBinary(program.getName(), imageData);
                    }
                }
                programMap.put(fileExtension, programInfo);
            }
            return programInfo.program == null ? null : programInfo;
        }
        return null;
    }
}
