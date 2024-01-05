<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("termsTitle")}
    <#elseif section = "form">
    <style>
        .card-pf {
            max-width: 1000px;
        }
        ul, ol {
            padding-left: 10px;
        }
        ul {
            list-style-type: circle;
        }
    </style>
    <div id="kc-terms-text">
        ${kcSanitize(msg("termsText"))?no_esc}
    </div>
    <form class="form-actions" action="${url.loginAction}" method="POST">
        <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="accept" id="kc-accept" type="submit" value="${msg("doAccept")}"/>
        <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel" id="kc-decline" type="submit" value="${msg("doDecline")}"/>
    </form>
    <div class="clearfix"></div>
    </#if>
</@layout.registrationLayout>