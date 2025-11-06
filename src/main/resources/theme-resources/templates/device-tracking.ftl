<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>
    <#if section = "header">
        Verifying Security
    <#elseif section = "form">
    <form id="kc-maxmind-form" method="post" action="${url.loginAction}">
        <div style="text-align: center; padding: 40px 20px;">
            <#--  <h2 style="margin-bottom: 16px; font-size: 24px;">Verifying Security</h2>  -->
            <p style="color: #6c757d; margin-bottom: 24px; font-size: 16px;">
                Please wait while we verify your login...
            </p>
            <div style="margin: 0 auto; border: 4px solid #f3f3f3; border-top: 4px solid #3498db; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite;"></div>
        </div>
        <input type="hidden" name="deviceSessionId" id="deviceSessionId" value="">
    </form>

    <style>
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
    </style>

    <script>
    // Initialize MaxMind Device Tracking with account ID BEFORE loading device.js
    // This follows the official MaxMind documentation pattern
    (function () {
        var mmapiws = (window.__mmapiws = window.__mmapiws || {});
        mmapiws.accountId = '${maxmindAccountId}';

        var loadDeviceJs = function () {
            var element = document.createElement('script');
            element.async = true;
            element.src = 'https://device.maxmind.com/js/device.js';
            document.body.appendChild(element);
            console.log('MaxMind device.js loading with account ID:', mmapiws.accountId);
        };

        if (window.addEventListener) {
            window.addEventListener('load', loadDeviceJs, false);
        } else if (window.attachEvent) {
            window.attachEvent('onload', loadDeviceJs);
        }
    })();

    // Wait for device tracking to complete, then submit form
    (function() {
        var maxAttempts = ${maxAttempts}; // From authenticator config
        var attempts = 0;
        var deviceSessionId = null;

        // Function to get cookie value by name
        function getCookie(name) {
            var value = '; ' + document.cookie;
            var parts = value.split('; ' + name + '=');
            if (parts.length === 2) {
                return parts.pop().split(';').shift();
            }
            return null;
        }

        function trySubmit() {
            attempts++;

            // Try to get device session ID from cookie (MaxMind stores it as __mmapiwsid)
            deviceSessionId = getCookie('__mmapiwsid');

            if (deviceSessionId) {
                console.log('MaxMind Device Session ID captured from cookie:', deviceSessionId);
                submitForm();
                return;
            }

            // Also try localStorage as fallback
            try {
                if (window.localStorage) {
                    var storedId = localStorage.getItem('__mmapiwsid');
                    if (storedId) {
                        deviceSessionId = storedId;
                        console.log('MaxMind Device Session ID captured from localStorage:', deviceSessionId);
                        submitForm();
                        return;
                    }
                }
            } catch (e) {
                console.error('Error reading localStorage:', e);
            }

            // Retry or timeout
            if (attempts < maxAttempts) {
                setTimeout(trySubmit, 100);
            } else {
                console.warn('MaxMind device tracking timeout after ' + (maxAttempts / 10) + ' seconds - proceeding without device ID');
                submitForm();
            }
        }

        function submitForm() {
            var form = document.getElementById('kc-maxmind-form');
            if (!form) {
                console.error('MaxMind form not found');
                return;
            }

            // Add device session ID as hidden field
            if (deviceSessionId) {
                document.getElementById('deviceSessionId').value = deviceSessionId;
                console.log('Submitting form with device session ID:', deviceSessionId);
            } else {
                console.log('Submitting form WITHOUT device session ID');
            }

            form.submit();
        }

        // Start trying to capture device ID when DOM is ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', trySubmit);
        } else {
            trySubmit();
        }
    })();
    </script>
    </#if>
</@layout.registrationLayout>
