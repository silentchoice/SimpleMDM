package com.simplemdm.service.mdm;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<Long> currentSystemUserId();
}
