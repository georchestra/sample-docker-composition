<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet
  version="3.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:local="urn:maelstro:local"
  exclude-result-prefixes="xs local"
>
  <xsl:mode on-no-match="shallow-copy"/>

  <xsl:variable
    name="source-url"
    as="xs:string"
    select="string(environment-variable('SOURCE_GEOSERVER_PUBLIC_URL'))"
  />
  <xsl:variable
    name="destination-url"
    as="xs:string"
    select="string(environment-variable('DESTINATION_GEOSERVER_URL'))"
  />

  <xsl:function name="local:replace-literal" as="xs:string">
    <xsl:param name="input" as="xs:string"/>
    <xsl:param name="search" as="xs:string"/>
    <xsl:param name="replacement" as="xs:string"/>
    <xsl:choose>
      <xsl:when test="$search ne '' and contains($input, $search)">
        <xsl:sequence
          select="concat(
            substring-before($input, $search),
            $replacement,
            local:replace-literal(substring-after($input, $search), $search, $replacement)
          )"
        />
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="$input"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <xsl:template match="@*">
    <xsl:attribute name="{name()}" namespace="{namespace-uri()}">
      <xsl:value-of select="local:replace-literal(string(.), $source-url, $destination-url)"/>
    </xsl:attribute>
  </xsl:template>

  <xsl:template match="text()">
    <xsl:value-of select="local:replace-literal(string(.), $source-url, $destination-url)"/>
  </xsl:template>
</xsl:stylesheet>
