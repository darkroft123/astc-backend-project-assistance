package com.smms.assistance.infrastructure.controller.rest.dto;

import jakarta.ws.rs.FormParam;

import java.io.InputStream;

public class CsvUploadForm {

    @FormParam("file")
    public InputStream file;

    @FormParam("fileName")
    public String fileName;
}
