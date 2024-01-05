<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','email','firstName','lastName'); section>
    <#if section = "header">
        ${msg("loginProfileTitle")}
    <#elseif section = "form">
        <form id="kc-update-profile-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <#if user.editUsernameAllowed>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcLabelWrapperClass!}">
                        <label for="username" class="${properties.kcLabelClass!}">${msg("username")}</label>
                    </div>
                    <div class="${properties.kcInputWrapperClass!}">
                        <input type="text" id="username" name="username" value="${(user.username!'')}"
                               class="${properties.kcInputClass!}"
                               aria-invalid="<#if messagesPerField.existsError('username')>true</#if>"
                        />

                        <#if messagesPerField.existsError('username')>
                            <span id="input-error-username" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('username'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </div>
            </#if>
            <#if user.editEmailAllowed>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcLabelWrapperClass!}">
                        <label for="email" class="${properties.kcLabelClass!}">${msg("email")}</label>
                    </div>
                    <div class="${properties.kcInputWrapperClass!}">
                        <input type="text" id="email" name="email" value="${(user.email!'')}"
                               class="${properties.kcInputClass!}"
                               aria-invalid="<#if messagesPerField.existsError('email')>true</#if>"
                        />

                        <#if messagesPerField.existsError('email')>
                            <span id="input-error-email" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('email'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </div>
            </#if>

            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="firstName" class="${properties.kcLabelClass!}">${msg("firstName")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input type="text" id="firstName" name="firstName" value="${(user.firstName!'')}"
                           class="${properties.kcInputClass!}"
                           aria-invalid="<#if messagesPerField.existsError('firstName')>true</#if>"
                    />

                    <#if messagesPerField.existsError('firstName')>
                        <span id="input-error-firstname" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('firstName'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="lastName" class="${properties.kcLabelClass!}">${msg("lastName")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input type="text" id="lastName" name="lastName" value="${(user.lastName!'')}"
                           class="${properties.kcInputClass!}"
                           aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>"
                    />

                    <#if messagesPerField.existsError('lastName')>
                        <span id="input-error-lastname" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('lastName'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>


            <!-- when upgrading keycloak we might need to apply this change on the new `login-update-propfile.ftl` -->
            <!-- Attention: When you change this, also adjust in `register.ftl` -->
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="user.attributes.municipality" class="${properties.kcLabelClass!}">${msg("municipality")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <select
                        id="user.attributes.municipality"
                        class="${properties.kcInputClass!}"
                        name="user.attributes.municipality"
                        value="${(user.attributes.municipality!'')}">
                        <option value="" disabled <#if !(user.attributes.municipality!)?has_content>selected</#if>>${msg("pleaseSelect")}</option>
                        <option value="NONE" <#if (user.attributes.municipality!)?has_content && (user.attributes.municipality!'') == 'NONE'>selected</#if>>${msg("notSpecified")}</option>
                        <option value="koethen" <#if (user.attributes.municipality!)?has_content && (user.attributes.municipality!'') == 'koethen'>selected</#if>>Köthen</option>
                        <option value="schkeuditz" <#if (user.attributes.municipality!)?has_content && (user.attributes.municipality!'') == 'schkeuditz'>selected</#if>>Schkeuditz</option>
                    </select>

                    <#if messagesPerField.existsError('municipality')>
                        <span id="input-error-municipality" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('user.attributes.municipality'))?no_esc}
                        </span>
                    </#if>
                    <!-- for the custom check below -->
                    <span id="input-error-municipality_custom" class="${properties.kcInputErrorMessageClass!}" aria-live="polite" style="display: none;">
                        ${msg("pleaseSelectAnOption")}
                    </span>
                </div>
            </div>
            <!-- simple client-side only check, as long as no harm can be done surpassing this -->
            <script type="text/javascript">
                document.getElementById("kc-update-profile-form").addEventListener("submit", function(event) {
                    var municipality = document.getElementById("user.attributes.municipality").value;
                    var errorMsg = document.getElementById("input-error-municipality_custom");
                    if (municipality === "") {
                        errorMsg.style.display = "block";
                        errorMsg.focus()
                        event.preventDefault();
                    } else {
                        errorMsg.style.display = "none";
                    }
                });
            </script>


            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                    </div>
                </div>

                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <#if isAppInitiatedAction??>
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doSubmit")}" />
                    <button class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" type="submit" name="cancel-aia" value="true" />${msg("doCancel")}</button>
                    <#else>
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doSubmit")}" />
                    </#if>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>