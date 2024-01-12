<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName','email','username','password','password-confirm'); section>
    <#if section = "header">
        ${msg("registerTitle")}
    <#elseif section = "form">
        <form id="kc-register-form" class="${properties.kcFormClass!}" action="${url.registrationAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="firstName" class="${properties.kcLabelClass!}">${msg("firstName")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input type="text" id="firstName" class="${properties.kcInputClass!}" name="firstName"
                           value="${(register.formData.firstName!'')}"
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
                    <input type="text" id="lastName" class="${properties.kcInputClass!}" name="lastName"
                           value="${(register.formData.lastName!'')}"
                           aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>"
                    />

                    <#if messagesPerField.existsError('lastName')>
                        <span id="input-error-lastname" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('lastName'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>

            <!-- when upgrading keycloak we might need to apply this change on the new `register.ftl` -->
            <!-- needs to start with `user.attributes` or else the data is not stored -->
            <!-- Attention: When you change this, also adjust in `login-update-propfile.ftl` -->
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="user.attributes.municipality" class="${properties.kcLabelClass!}">${msg("municipality")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <select
                        id="user.attributes.municipality"
                        class="${properties.kcInputClass!}"
                        name="user.attributes.municipality"
                        value="${(register.formData['user.attributes.municipality']!'')}">
                            <option value="" disabled <#if !(register.formData['user.attributes.municipality']!)?has_content>selected</#if>>${msg("pleaseSelect")}</option>
                            <option value="NONE" <#if (register.formData['user.attributes.municipality']!)?has_content && (register.formData['user.attributes.municipality']!'') == 'NONE'>selected</#if>>${msg("notSpecified")}</option>
                            <option value="koethen" <#if (register.formData['user.attributes.municipality']!)?has_content && (register.formData['user.attributes.municipality']!'') == 'koethen'>selected</#if>>Köthen</option>
                            <option value="schkeuditz" <#if (register.formData['user.attributes.municipality']!)?has_content && (register.formData['user.attributes.municipality']!'') == 'schkeuditz'>selected</#if>>Schkeuditz</option>
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
                document.getElementById("kc-register-form").addEventListener("submit", function(event) {
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
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="email" class="${properties.kcLabelClass!}">${msg("email")}</label>
                </div>
                <div class="${properties.kcInputWrapperClass!}">
                    <input type="text" id="email" class="${properties.kcInputClass!}" name="email"
                           value="${(register.formData.email!'')}" autocomplete="email"
                           aria-invalid="<#if messagesPerField.existsError('email')>true</#if>"
                    />

                    <#if messagesPerField.existsError('email')>
                        <span id="input-error-email" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('email'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>

            <#if !realm.registrationEmailAsUsername>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcLabelWrapperClass!}">
                        <label for="username" class="${properties.kcLabelClass!}">${msg("username")}</label>
                    </div>
                    <div class="${properties.kcInputWrapperClass!}">
                        <input type="text" id="username" class="${properties.kcInputClass!}" name="username"
                               value="${(register.formData.username!'')}" autocomplete="username"
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

            <#if passwordRequired??>
                <!-- added toggle icon to show password [RFR-747] -->
                <script>
                document.addEventListener("DOMContentLoaded", function() {

                    function togglePasswordFieldVisibility(fieldId, toggleIconId) {
                        var passwordField = document.getElementById(fieldId);
                        var toggleIcon = document.getElementById(toggleIconId);

                        if (passwordField.type === "password") {
                            passwordField.type = "text";
                            toggleIcon.src = "${url.resourcesPath}/img/fa-eye-slash.svg";
                            toggleIcon.classList.add("eye-slash");
                        } else {
                            passwordField.type = "password";
                            toggleIcon.src = "${url.resourcesPath}/img/fa-eye.svg";
                            toggleIcon.classList.remove("eye-slash");
                        }
                    }

                    function toggleBothFields() {
                        togglePasswordFieldVisibility("password", "togglePassword");
                        togglePasswordFieldVisibility("password-confirm", "togglePasswordConfirm");
                    }

                    document.getElementById("togglePassword").addEventListener("click", toggleBothFields);
                    document.getElementById("togglePasswordConfirm").addEventListener("click", toggleBothFields);
                });
                </script>
                <style>
                    /* Increase hit-area for the toggle icon for mobile support */
                    .passwordToggle {
                        /* ensure the icon does not move when switched: */
                        width: 16px;
                        height: 16px;
                        /* position icon */
                        position: absolute;
                        top: 50%;
                        right: 30px;
                        transform: translateY(-50%);
                        cursor: pointer;
                    }

                    /* ensure the eye keeps its size when toggling to eye-slash */
                    .passwordToggle.eye-slash {
                        transform: translateY(-50%) scale(1.1) !important;
                    }
                </style>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcLabelWrapperClass!}">
                        <label for="password" class="${properties.kcLabelClass!}">${msg("password")}</label>
                    </div>
                    <div class="${properties.kcInputWrapperClass!}" style="position: relative;">
                        <input type="password" id="password" class="${properties.kcInputClass!}" name="password"
                            autocomplete="new-password"
                            aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"
                        />
                        <img id="togglePassword" class="passwordToggle" src="${url.resourcesPath}/img/fa-eye.svg">
                        <#if messagesPerField.existsError('password')>
                            <span id="input-error-password" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('password'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </div>

                <div class="${properties.kcFormGroupClass!}">
                    <div class="${properties.kcLabelWrapperClass!}">
                        <label for="password-confirm" class="${properties.kcLabelClass!}">${msg("passwordConfirm")}</label>
                    </div>
                    <div class="${properties.kcInputWrapperClass!}" style="position: relative;">
                        <input type="password" id="password-confirm" class="${properties.kcInputClass!}" name="password-confirm"
                            aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"
                        />
                        <img id="togglePasswordConfirm" class="passwordToggle" src="${url.resourcesPath}/img/fa-eye.svg">
                        <#if messagesPerField.existsError('password-confirm')>
                            <span id="input-error-password-confirm" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
                            </span>
                        </#if>
                    </div>
                </div>
            </#if>

            <#if recaptchaRequired??>
                <div class="form-group">
                    <div class="${properties.kcInputWrapperClass!}">
                        <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}"></div>
                    </div>
                </div>
            </#if>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-options" class="${properties.kcFormOptionsClass!}">
                    <div class="${properties.kcFormOptionsWrapperClass!}">
                        <span><a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a></span>
                    </div>
                </div>

                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doRegister")}"/>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>