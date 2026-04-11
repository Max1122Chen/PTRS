package com.travel.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.service.PoiTypeService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 classpath 配置加载 POI 类型字典，作为后端统一来源。
 */
@Service
public class PoiTypeServiceImpl implements PoiTypeService
{

    private static final Logger log = LoggerFactory.getLogger(PoiTypeServiceImpl.class);

    private static final String POI_TYPES_RESOURCE = "config/poi-types.json";

    private final ObjectMapper objectMapper;

    private List<Map<String, Object>> cachedTypes = Collections.emptyList();

    private Map<String, String> cachedLabelMap = Collections.emptyMap();

    public PoiTypeServiceImpl(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init()
    {
        try
        {
            ClassPathResource resource = new ClassPathResource(POI_TYPES_RESOURCE);
            try (InputStream inputStream = resource.getInputStream())
            {
                PoiTypeCatalog catalog = objectMapper.readValue(inputStream, PoiTypeCatalog.class);
                loadCatalog(catalog);
            }
            log.info("Loaded POI type catalog from classpath:{} with {} entries", POI_TYPES_RESOURCE, cachedTypes.size());
        }
        catch (Exception ex)
        {
            log.error("Failed to load POI type catalog from classpath:{}, fallback to defaults", POI_TYPES_RESOURCE, ex);
            loadCatalog(defaultCatalog());
        }
    }

    @Override
    public List<Map<String, Object>> listPoiTypes()
    {
        return cachedTypes;
    }

    @Override
    public Map<String, String> codeLabelMap()
    {
        return cachedLabelMap;
    }

    private void loadCatalog(PoiTypeCatalog catalog)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, String> labelMap = new HashMap<>();

        if (catalog != null && catalog.getTypes() != null)
        {
            for (PoiTypeItem item : catalog.getTypes())
            {
                if (item == null || item.getCode() == null || item.getCode().isBlank())
                {
                    continue;
                }
                String code = item.getCode().trim().toLowerCase();
                String label = item.getLabel() == null ? code : item.getLabel().trim();

                Map<String, Object> one = new HashMap<>();
                one.put("code", code);
                one.put("label", label);
                one.put("category", item.getCategory());
                one.put("searchable", item.getSearchable());
                one.put("routeVisible", item.getRouteVisible());
                one.put("deprecated", item.getDeprecated());
                out.add(Collections.unmodifiableMap(one));

                labelMap.put(code, label);
            }
        }

        this.cachedTypes = Collections.unmodifiableList(out);
        this.cachedLabelMap = Collections.unmodifiableMap(labelMap);
    }

    private PoiTypeCatalog defaultCatalog()
    {
        PoiTypeCatalog catalog = new PoiTypeCatalog();
        List<PoiTypeItem> types = new ArrayList<>();
        types.add(item("scenic_spot", "景点", "business", true, true, false));
        types.add(item("gate", "出入口", "business", true, true, false));
        types.add(item("library", "图书馆", "business", true, true, false));
        types.add(item("teaching", "教学楼", "business", true, true, false));
        types.add(item("canteen", "食堂", "business", true, true, false));
        types.add(item("service", "服务点", "business", true, true, false));
        types.add(item("toilet", "厕所", "business", true, true, false));
        types.add(item("dormitory", "宿舍", "business", true, true, false));
        types.add(item("lab", "实验楼", "business", true, true, false));
        types.add(item("virtual_node", "道路辅助节点", "system", false, false, false));
        catalog.setTypes(types);
        return catalog;
    }

    private PoiTypeItem item(String code, String label, String category, Boolean searchable, Boolean routeVisible, Boolean deprecated)
    {
        PoiTypeItem one = new PoiTypeItem();
        one.setCode(code);
        one.setLabel(label);
        one.setCategory(category);
        one.setSearchable(searchable);
        one.setRouteVisible(routeVisible);
        one.setDeprecated(deprecated);
        return one;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PoiTypeCatalog
    {

        private String version;

        private List<PoiTypeItem> types;

        private Map<String, String> aliases;

        public String getVersion()
        {
            return version;
        }

        public void setVersion(String version)
        {
            this.version = version;
        }

        public List<PoiTypeItem> getTypes()
        {
            return types;
        }

        public void setTypes(List<PoiTypeItem> types)
        {
            this.types = types;
        }

        public Map<String, String> getAliases()
        {
            return aliases;
        }

        public void setAliases(Map<String, String> aliases)
        {
            this.aliases = aliases;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PoiTypeItem
    {

        private String code;

        private String label;

        private String category;

        private Boolean searchable;

        private Boolean routeVisible;

        private Boolean deprecated;

        public String getCode()
        {
            return code;
        }

        public void setCode(String code)
        {
            this.code = code;
        }

        public String getLabel()
        {
            return label;
        }

        public void setLabel(String label)
        {
            this.label = label;
        }

        public String getCategory()
        {
            return category;
        }

        public void setCategory(String category)
        {
            this.category = category;
        }

        public Boolean getSearchable()
        {
            return searchable;
        }

        public void setSearchable(Boolean searchable)
        {
            this.searchable = searchable;
        }

        public Boolean getRouteVisible()
        {
            return routeVisible;
        }

        public void setRouteVisible(Boolean routeVisible)
        {
            this.routeVisible = routeVisible;
        }

        public Boolean getDeprecated()
        {
            return deprecated;
        }

        public void setDeprecated(Boolean deprecated)
        {
            this.deprecated = deprecated;
        }
    }
}
