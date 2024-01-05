<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("emailVerifyTitle")}
    <#elseif section = "form">
        <p class="instruction">${msg("emailVerifyInstruction1",user.email)}</p> 
    <#elseif section = "info">
        <p class="instruction">
            ${msg("emailVerifyInstruction2")}
            <br/>
            <a href="${url.loginAction}">${msg("doClickHere")}</a> ${msg("emailVerifyInstruction3")}
        </p>
	<a href="de.cyface.app.r4r:/registered" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}">${msg("backToLoginScreen")}</a>
    </#if>
</@layout.registrationLayout>
